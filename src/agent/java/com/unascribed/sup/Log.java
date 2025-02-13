package com.unascribed.sup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.unascribed.sup.pieces.NullPrintStream;

/**
 * Hand-rolled bare-minimum logger facility.
 */
public class Log {

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
	private static PrintStream fileStream;
	private static String defaultTag;
	
	public static void init() {
		defaultTag = Agent.standalone ? "sync" : "agent";
		File logTarget = new File("logs");
		if (!logTarget.isDirectory()) {
			logTarget = new File(".");
		}
		File logFile = new File(logTarget, "unsup.log");
		File oldLogFile = new File(logTarget, "unsup.log.1");
		File olderLogFile = new File(logTarget, "unsup.log.2");
		// intentionally ignoring exceptional return here
		// don't really care if any of it fails
		if (logFile.exists()) {
			if (oldLogFile.exists()) {
				if (olderLogFile.exists()) {
					olderLogFile.delete();
				}
				oldLogFile.renameTo(olderLogFile);
			}
			logFile.renameTo(oldLogFile);
		}
		try {
			OutputStream logOut = new FileOutputStream(logFile);
			Agent.cleanup.add(logOut::close);
			fileStream = new PrintStream(logOut, true, "UTF-8");
		} catch (Exception e) {
			fileStream = NullPrintStream.INSTANCE;
			Log.warn("Failed to open log file "+logFile);
		}
	}

	public static void log(String flavor, String msg) {
		log(flavor, defaultTag, msg);
	}
	
	public static void log(String flavor, String msg, Throwable t) {
		log(flavor, defaultTag, msg, t);
	}
	
	public synchronized static void log(String flavor, String tag, String msg, Throwable t) {
		if (!("DEBUG".equals(flavor)) || SysProps.DEBUG) {
			t.printStackTrace();
		}
		t.printStackTrace(fileStream);
		log(tag, flavor, msg);
	}
	
	public synchronized static void log(String flavor, String tag, String msg) {
		String line = "["+dateFormat.format(new Date())+"] [unsup "+tag+"/"+flavor+"]: "+msg;
		if (!("DEBUG".equals(flavor)) || SysProps.DEBUG) System.out.println(line);
		fileStream.println(line);
	}
	

	
	public static void debug(String msg)                          { log("DEBUG", msg); }
	public static void debug(String msg, Throwable t)             { log("DEBUG", msg, t); }
	public static void debug(String tag, String msg, Throwable t) { log("DEBUG", tag, msg, t); }
	public static void debug(String tag, String msg)              { log("DEBUG", tag, msg); }
	
	public static void  info(String msg)                          { log( "INFO", msg); }
	public static void  info(String msg, Throwable t)             { log( "INFO", msg, t); }
	public static void  info(String tag, String msg, Throwable t) { log( "INFO", tag, msg, t); }
	public static void  info(String tag, String msg)              { log( "INFO", tag, msg); }
	
	public static void  warn(String msg)                          { log( "WARN", msg); }
	public static void  warn(String msg, Throwable t)             { log( "WARN", msg, t); }
	public static void  warn(String tag, String msg, Throwable t) { log( "WARN", tag, msg, t); }
	public static void  warn(String tag, String msg)              { log( "WARN", tag, msg); }
	
	public static void error(String msg)                          { log("ERROR", msg); }
	public static void error(String msg, Throwable t)             { log("ERROR", msg, t); }
	public static void error(String tag, String msg, Throwable t) { log("ERROR", tag, msg, t); }
	public static void error(String tag, String msg)              { log("ERROR", tag, msg); }
	
}
