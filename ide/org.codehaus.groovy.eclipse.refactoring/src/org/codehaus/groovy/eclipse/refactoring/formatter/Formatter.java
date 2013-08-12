package org.codehaus.groovy.eclipse.refactoring.formatter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Formatter {

    public static void main(String[] args) throws Exception {
        String code = readInFile(args[0]);
		String extension = args[0].substring(args[0].lastIndexOf(".") + 1, args[0].length());
		if (code != null && extension.equals("java")) {
			JavaFormat javaFormatter = new JavaFormat();
			javaFormatter.format(args[0], code);
			if (javaFormatter.isFormatted()) {
				createBackupFile(args[0], code);
			}

		} else if (code != null && extension.equals("groovy")) {
			GroovyFormat groovyFormatter = new GroovyFormat();
			groovyFormatter.format(args[0], code);
            if (groovyFormatter.isFormatted()) {
				createBackupFile(args[0], code);
			}

		} else {
			System.out.println("Sorry, no formatting could be applied to " + args[0]);
		}

	}

	@SuppressWarnings("resource")
	private static String readInFile(String fileName) {
		BufferedReader inStream = null;
		StringBuilder code = new StringBuilder("");
		String line;
		try {
			FileReader file = new FileReader(fileName);
			inStream = new BufferedReader(file);
			while ((line = inStream.readLine()) != null) {
				code.append(line + "\n");
			}
		} catch (IOException e) {
			System.out.println("Error occured when opening " + fileName);
			return null;
		}
		return code.toString();
	}

	public static void createBackupFile(String fileName, String before) {
		try {
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("MM_dd_yyyy_h_mm_ss");
			String formattedDate = sdf.format(date);
			String nameWithDate = fileName + "_BACKUP_" + formattedDate;
			FileWriter file = new FileWriter(nameWithDate);
			PrintWriter safety = new PrintWriter(file);
			safety.println(before);
			safety.close();
			// System.out.println("*** A backup file was placed in " +
			// nameWithDate + " ***");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
