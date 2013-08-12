package org.codehaus.groovy.eclipse.refactoring.formatter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class GroovyFormat {

	private boolean correctlyFormatted;

	public GroovyFormat() {
		correctlyFormatted = false;
	}


    public void format(String fileName, String code) {
			try {
            DefaultGroovyFormatter cf = initializeFormatter(code);
            TextEdit te = cf.format();
            IDocument dc = new Document(code.toString());
            if (te == null || code.length() == 0) {
                System.out.println("!!! Could not format " + fileName + " !!!");
            }
				te.apply(dc);

				PrintWriter out = new PrintWriter(new FileWriter(fileName));
				out.println(dc.get());
				out.close();

				System.out
						.println("*** Groovy standard formatting conventions have been applied to "
								+ fileName + " ***");
                correctlyFormatted = true;
			} catch (MalformedTreeException e) {
				e.printStackTrace();
			} catch (BadLocationException e) {
				e.printStackTrace();
        } catch (IOException e) {
				e.printStackTrace();
        } catch (NullPointerException e) {
            System.out.println("Cannot format " + fileName + ", probably due to compilation errors.  Please fix and try again.");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Cannot format " + fileName + ", probably due to compilation errors.  Please fix and try again.");
        }
	}

	public boolean isFormatted() {
		return correctlyFormatted;
	}

	public static void createBackupFile(String fileName, IDocument dc) {
		try {
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("MM_dd_yyyy_h_mm_ss");
			String formattedDate = sdf.format(date);
			String nameWithDate = fileName + "_BACKUP_" + formattedDate;
			FileWriter file = new FileWriter(nameWithDate);
			PrintWriter safety = new PrintWriter(file);
			safety.println(dc.get());
			safety.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static DefaultGroovyFormatter initializeFormatter(String code) {
		IPreferenceStore pref = null;
		FormatterPreferencesOnStore defaultPrefs = new FormatterPreferencesOnStore(
				pref);
        System.out.println("sfsdfsdfsdf" + defaultPrefs.isSmartPaste());
		IDocument doc = new Document(code.toString());
		TextSelection sel = new TextSelection(0, code.length());
		DefaultGroovyFormatter formatter = new DefaultGroovyFormatter(sel, doc,
				defaultPrefs, false);
		return formatter;

	}


}