package org.codehaus.groovy.eclipse.refactoring.formatter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class JavaFormat {
	private boolean correctlyFormatted;

	public JavaFormat() {
		correctlyFormatted = false;
	}

	public void format(String fileName, String code) {
		CodeFormatter cf = initializeFormatter();
		TextEdit te = cf.format(CodeFormatter.K_UNKNOWN, code, 0, code.length(), 0, System.getProperty("line.separator"));
		IDocument dc = new Document(code.toString());
		if (te == null || code.length() == 0) {
			System.out.println("!!! Could not format " + fileName + " !!!");
		} else {
			try {
				te.apply(dc);

				PrintWriter out = new PrintWriter(new FileWriter(fileName));
				out.println(dc.get());
				out.close();

				System.out.println("*** Java standard formatting conventions have been applied to " + fileName + " ***");
			} catch (MalformedTreeException e) {
				e.printStackTrace();
			} catch (BadLocationException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			correctlyFormatted = true;
		}
	}

	public boolean isFormatted() {
		return correctlyFormatted;
	}

	@SuppressWarnings({ "unchecked", "deprecation" })
	private static CodeFormatter initializeFormatter() {
		@SuppressWarnings("rawtypes")
		Map options = DefaultCodeFormatterConstants.getEclipseDefaultSettings();
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_7);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_7);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_7);
		options.put(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_SELECTOR_IN_METHOD_INVOCATION,
				DefaultCodeFormatterConstants.createAlignmentValue(false, DefaultCodeFormatterConstants.WRAP_NO_SPLIT, DefaultCodeFormatterConstants.INDENT_DEFAULT));

		options.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, 110);

		System.out.println(options.toString());
		final CodeFormatter codeFormatter = ToolFactory.createCodeFormatter(options);
		return codeFormatter;

	}

}
