package org.nem.nac.application;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class LogFile {

	private static LogFile _instance;

	public static LogFile instance() {
			if (_instance == null) {
			synchronized (LogFile.class) {
				if (_instance == null){
				_instance = new LogFile();
				}
			}
		}
	return _instance;
       }

	private final File _logFile;

	private LogFile() {
		final File filesDir = NacApplication.getAppContext().getFilesDir();
		if (!filesDir.exists()) { filesDir.mkdirs(); }
		_logFile = new File(filesDir, AppConstants.LOG_FILE_NAME);
	}

	public synchronized String read()
			throws IOException {
		if (!_logFile.exists()) {
			return "";
		}

		final StringBuilder sb = new StringBuilder();
		BufferedReader br;
		String receiveString;
		final FileReader fileReader = new FileReader(_logFile);
		br = new BufferedReader(fileReader);
		while ((receiveString = br.readLine()) != null) {
			sb.append(receiveString).append('\n');
		}
		fileReader.close();
		br.close();
		return sb.toString();
	}

	public synchronized void write(final String string)
			throws IOException {
		if (_logFile.length() > 1000000) {
			_logFile.delete();
		}
		if (!_logFile.exists()) {
			_logFile.createNewFile();
		}
		//
		BufferedWriter buf = null;
		try {
			buf = new BufferedWriter(new FileWriter(_logFile, true));
			//BufferedWriter for performance, true to set append to file flag
			buf.append(string);
			buf.newLine();
		} finally {
			if (buf != null) {
				try {
					buf.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
