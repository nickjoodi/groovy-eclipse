/*******************************************************************************
 * Copyright (c) 2009 Codehaus.org, SpringSource, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andy Clement        - Initial API and implementation
 *     Andrew Eisenberg - Additional work
 *******************************************************************************/
package org.codehaus.jdt.groovy.internal.compiler.ast;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import groovy.lang.GroovyClassLoader;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.CompilationUnit.ProgressListener;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.groovy.core.util.GroovyUtils;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.core.builder.BatchImageBuilder;
import org.eclipse.jdt.internal.core.builder.BuildNotifier;

/**
 * The mapping layer between the groovy parser and the JDT. This class communicates with the groovy parser and translates results
 * back for JDT to consume.
 * 
 * @author Andy Clement
 */
@SuppressWarnings("restriction")
public class GroovyParser {

	// LookupEnvironment lookupEnvironment;
	public ProblemReporter problemReporter;
	CompilationUnit groovyCompilationUnit;
	private JDTResolver resolver;
	private String gclClasspath;
	public static IGroovyDebugRequestor debugRequestor;
	private CompilerOptions compilerOptions;
	private Object requestor;

	/*
	 * Each project is allowed a GroovyClassLoader that will be used to load transform definitions and supporting classes. A cache
	 * is maintained from project names to the current classpath and associated loader. If the classpath matches the cached version
	 * on a call to build a parser then it is reused. If it does not match then a new loader is created and stored (storing it
	 * orphans the previously cached one). When either a full build or a clean or project close occurs, we also discard the loader
	 * instances associated with the project.
	 */

	private static Map<String, PathLoaderPair> projectToLoaderCache = Collections
			.synchronizedMap(new HashMap<String, PathLoaderPair>());

	static class PathLoaderPair {
		String classpath;
		GroovyClassLoader groovyClassLoader;

		PathLoaderPair(String classpath) {
			this.classpath = classpath;
			this.groovyClassLoader = new GroovyClassLoader();
			configureClasspath(groovyClassLoader, classpath);
		}
	}

	/**
	 * Remove all cached classloaders for this project
	 */
	public static void tidyCache(String projectName) {
		// This will orphan the loader on the heap
		PathLoaderPair removed = projectToLoaderCache.remove(projectName);
		// System.out.println("Cleaning up loader for project " + projectName + "?" + (removed == null ? "no" : "yes"));
	}

	public GroovyParser(CompilerOptions options, ProblemReporter problemReporter) {
		this(null, options, problemReporter);
	}

	// FIXASC (RC1) review callers who pass null for options
	public GroovyParser(Object requestor, CompilerOptions options, ProblemReporter problemReporter) {
		String path = (options == null ? null : options.groovyClassLoaderPath);
		this.requestor = requestor;
		// FIXASC (M2) set parent of the loader to system or context class loader?

		// record any paths we use for a project so that when the project is cleared,
		// the paths (which point to cached classloaders) can be cleared
		GroovyClassLoader gcl = null;
		if (path != null) {
			String projectName = options.groovyProjectName;
			if (projectName == null) {
				// throw new IllegalStateException("Cannot build without knowing project name");
			} else {
				PathLoaderPair pathAndLoader = projectToLoaderCache.get(projectName);
				if (pathAndLoader == null) {
					pathAndLoader = new PathLoaderPair(path);
					projectToLoaderCache.put(projectName, pathAndLoader);
				} else {
					if (!path.equals(pathAndLoader.classpath)) {
						// classpath change detected
						// System.out.println("Classpath change detected for " + projectName);
						pathAndLoader = new PathLoaderPair(path);
						projectToLoaderCache.put(projectName, pathAndLoader);
					}
				}
				// System.out.println("Using loader with path " + pathAndLoader.classpath);
				gcl = pathAndLoader.groovyClassLoader;
			}
		}
		this.gclClasspath = path;
		this.compilerOptions = options;
		// FIXASC (M2) Grab support
		GroovyClassLoader grabbyLoader = null;// avoid this for now: new GrapeAwareGroovyClassLoader();
		this.groovyCompilationUnit = new CompilationUnit(null, null, grabbyLoader, gcl);
		this.groovyCompilationUnit.removeOutputPhaseOperation();
		if ((options.groovyFlags & 0x01) != 0) {
			// its probably grails!
			// nothing up my sleeve, abracadabra!
			this.groovyCompilationUnit.addPhaseOperation(new GrailsInjector(gcl), Phases.CANONICALIZATION);
		}
		// this.lookupEnvironment = lookupEnvironment;
		this.problemReporter = problemReporter;
		this.resolver = new JDTResolver(groovyCompilationUnit);
		// groovyCompilationUnit.setClassLoader(gcl);
		groovyCompilationUnit.setResolveVisitor(resolver);
	}

	static class GrapeAwareGroovyClassLoader extends GroovyClassLoader {

		public boolean grabbed = false;

		@Override
		public void addURL(URL url) {
			this.grabbed = true;
			super.addURL(url);
		}

	}

	// FIXASC (RC1) perf ok?
	private static void configureClasspath(GroovyClassLoader gcl, String path) {
		if (path != null) {
			if (path.indexOf(File.pathSeparator) != -1) {
				int pos = 0;
				while (pos != -1) {
					int nextSep = path.indexOf(File.pathSeparator, pos);
					if (nextSep == -1) {
						// last piece
						String p = path.substring(pos);
						gcl.addClasspath(p);
						pos = -1;
					} else {
						String p = path.substring(pos, nextSep);
						gcl.addClasspath(p);
						pos = nextSep + 1;
					}
				}
			} else {
				gcl.addClasspath(path);
			}
		}
	}

	/**
	 * Call the groovy parser to drive the first few phases of
	 */
	public CompilationUnitDeclaration dietParse(ICompilationUnit sourceUnit, CompilationResult compilationResult) {
		char[] sourceCode = sourceUnit.getContents();
		if (sourceCode == null) {
			sourceCode = CharOperation.NO_CHAR; // pretend empty from thereon
		}

		// FIXASC (M3) need our own tweaked subclass of CompilerConfiguration?
		CompilerConfiguration groovyCompilerConfig = new CompilerConfiguration();
		// groovyCompilerConfig.setPluginFactory(new ErrorRecoveredCSTParserPluginFactory(null));
		ErrorCollector errorCollector = new GroovyErrorCollectorForJDT(groovyCompilerConfig);
		String filepath = null;

		// This check is necessary because the filename is short (as in the last part, eg. Foo.groovy) for types coming in
		// from the hierarchy resolver. If there is the same type in two different packages then the compilation process
		// is going to go wrong because the filename is used as a key in some groovy data structures. This can lead to false
		// complaints about the same file defining duplicate types.
		if (sourceUnit instanceof org.eclipse.jdt.internal.compiler.batch.CompilationUnit) {
			filepath = new String(((org.eclipse.jdt.internal.compiler.batch.CompilationUnit) sourceUnit).fileName);
		} else {
			filepath = new String(sourceUnit.getFileName());
		}

		SourceUnit groovySourceUnit = new SourceUnit(filepath, new String(sourceCode), groovyCompilerConfig, groovyCompilationUnit
				.getClassLoader(), errorCollector);
		GroovyCompilationUnitDeclaration gcuDeclaration = new GroovyCompilationUnitDeclaration(problemReporter, compilationResult,
				sourceCode.length, groovyCompilationUnit, groovySourceUnit, compilerOptions);
		// FIXASC (M2) get this from the Antlr parser
		compilationResult.lineSeparatorPositions = GroovyUtils.getSourceLineSeparatorsIn(sourceCode);
		groovyCompilationUnit.addSource(groovySourceUnit);

		// Check if it is worth plugging in a callback listener for parse/geneation
		if (requestor instanceof org.eclipse.jdt.internal.compiler.Compiler) {
			org.eclipse.jdt.internal.compiler.Compiler compiler = ((org.eclipse.jdt.internal.compiler.Compiler) requestor);
			if (compiler.requestor instanceof BatchImageBuilder) {
				BuildNotifier notifier = ((BatchImageBuilder) compiler.requestor).notifier;
				if (notifier != null) {
					groovyCompilationUnit.setProgressListener(new ProgressListenerImpl(notifier));
				}
			}
		}
		gcuDeclaration.processToPhase(Phases.CONVERSION);

		// Groovy moduleNode is null when there is a fatal error
		// Otherwise, recover what we can
		if (gcuDeclaration.getModuleNode() != null) {
			gcuDeclaration.populateCompilationUnitDeclaration();
			for (TypeDeclaration decl : gcuDeclaration.types) {
				GroovyTypeDeclaration gtDeclaration = (GroovyTypeDeclaration) decl;
				resolver.record(gtDeclaration);
			}
		}
		if (debugRequestor != null) {
			debugRequestor.acceptCompilationUnitDeclaration(gcuDeclaration);
		}
		return gcuDeclaration;
	}

	/**
	 * ProgressListener is called back when parsing of a file or generation of a classfile completes. By calling back to the build
	 * notifier we ignore those long pauses where it look likes it has hung!
	 * 
	 * Note: this does not move the progress bar, it merely updates the text
	 */
	static class ProgressListenerImpl implements ProgressListener {

		private BuildNotifier notifier;

		public ProgressListenerImpl(BuildNotifier notifier) {
			this.notifier = notifier;
		}

		public void parseComplete(int phase, String sourceUnitName) {
			try {
				// Chop it down to the containing package folder
				int lastSlash = sourceUnitName.lastIndexOf("/");
				if (lastSlash == -1) {
					lastSlash = sourceUnitName.lastIndexOf("\\");
				}
				if (lastSlash != -1) {
					StringBuffer msg = new StringBuffer();
					msg.append("Parsing groovy source in ");
					msg.append(sourceUnitName, 0, lastSlash);
					notifier.subTask(msg.toString());
				}
			} catch (Exception e) {
				// doesn't matter
			}
			notifier.checkCancel();
		}

		public void generateComplete(int phase, ClassNode classNode) {
			try {
				String pkgName = classNode.getPackageName();
				if (pkgName != null && pkgName.length() > 0) {
					StringBuffer msg = new StringBuffer();
					msg.append("Generating groovy classes in ");
					msg.append(pkgName);
					notifier.subTask(msg.toString());
				}
			} catch (Exception e) {
				// doesn't matter
			}
			notifier.checkCancel();
		}
	}

	public void reset() {
		GroovyClassLoader gcl = new GroovyClassLoader();
		configureClasspath(gcl, gclClasspath);
		this.groovyCompilationUnit = new CompilationUnit(gcl);
		this.resolver = new JDTResolver(groovyCompilationUnit);
		this.groovyCompilationUnit.setResolveVisitor(resolver);
	}

	public CompilerOptions getCompilerOptions() {
		return compilerOptions;
	}
}
