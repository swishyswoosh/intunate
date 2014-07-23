package com.quietwisdom.intunate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.cli.*;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;


public final class Intunate {

	public static final String DEFAULT_DELIMITER = "\t";
	public static final String NULL_STRING = "NULL";

	public static void main(String... args) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		
		File startingDirectory = null;
		String outFileName = null;
		String delim = DEFAULT_DELIMITER;
		try {
			Options opts = new Options();
			opts.addOption( "t", "delimiter", true, "delimiter for output (default is tab)");
			opts.addOption( "o", "outfile", true, "output filename");
			opts.addOption( "d", "directory", true, "input directory path (enclose in quotes if spaces are in path)");
						
			CommandLineParser parser = new GnuParser();
			CommandLine cmd = parser.parse(opts, args);
			

			if(cmd.hasOption("o")) {
				outFileName = cmd.getOptionValue("o");
			}
			if(null == outFileName) usage(opts);
			
			if(cmd.hasOption("t")) {
				String sep = cmd.getOptionValue("t");
				if(null != sep) delim = sep;
			}

			if(cmd.hasOption("d")) {
				String dir = cmd.getOptionValue("d");
				if(null == dir) usage(opts);

				startingDirectory = new File(dir);
				if(!startingDirectory.isDirectory() || !startingDirectory.exists() || !startingDirectory.canRead()) {
					System.err.println("Directory/Folder not found: " + dir);
					usage(opts);
				}
				
			} else {
				usage(opts);
			}
			if(null == outFileName) usage(opts);
		}
		catch( ParseException exp ) {
		    System.out.println( "Unexpected exception:" + exp.getMessage() );
		}
		
		

		PrintWriter pw = new PrintWriter(new FileWriter(outFileName));

		boolean header = true;
		for (File file : Intunate.getFileListing(startingDirectory)) {
			if(file.isDirectory()) continue;
			if(!file.toString().endsWith(".mp3") && !file.toString().endsWith(".MP3")) continue;
			if(!file.exists() || !file.canRead()) {
				System.out.println("Cannot read file: " + file.toString());
				continue;
			}
			
			md.reset();

//			System.out.format("File: %s\t%d\n", file.toString(), file.length());
			String escapedFilename = escapeFilename(file.toString());
//			System.out.println("File: " + escapedFilename);
			Mp3File mp3 = new Mp3File(file.toString());
			MP3FileWrapper fw = new MP3FileWrapper(file, mp3, md);
//			System.out.println("\tFileHash: " + fw.getHexFileHash());
//			System.out.println("\tContentHash: " + fw.getHexContentHash());

			SortedMap<String, String> info = getFileInfo(fw);
			info.put("Filename", file.toString());
			info.put("EscapedFilename", escapedFilename);
			info.put("FileLength", Long.toString(file.length()));
			
			printInfo(info, pw, delim, header);
			if(header) header = false;

			System.out.format("File Processed: %s\n", info.get("EscapedFilename"));

		}
		System.out.println();
		
		if(null != pw) pw.close();

	}

	private static void usage(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "Intunate", opts );
		System.exit(0);
	}

	private static void printInfo(SortedMap<String, String> info, PrintWriter pw, String delim, boolean header) {
		if(header) {
			for (String k : info.keySet()) {
				pw.print(k);
				pw.print(delim);
			}
			pw.println();
		}
		for (String k : info.keySet()) {
			String val = info.get(k);
			if(null == val) val = NULL_STRING;
			if(val.contains(delim)) val.replaceAll(delim, "?");
			val = stripBadChars(val);
			pw.print(val);
			pw.print(delim);
		}
		pw.println();
		pw.flush();
	}
	
	private static String stripBadChars(String s) {
		StringBuilder newString = new StringBuilder(s.length());
		for (int offset = 0; offset < s.length();) {
		    int codePoint = s.codePointAt(offset);
		    offset += Character.charCount(codePoint);
	
		    // Replace invisible control characters and unused code points
		    switch (Character.getType(codePoint))
		    {
		        case Character.CONTROL:     // \p{Cc}
		        case Character.FORMAT:      // \p{Cf}
		        case Character.PRIVATE_USE: // \p{Co}
		        case Character.SURROGATE:   // \p{Cs}
		        case Character.UNASSIGNED:  // \p{Cn}
		            newString.append('?');
		            break;
		        default:
		            newString.append(Character.toChars(codePoint));
		            break;
		    }
		}
		return newString.toString();
	}	

	private static SortedMap<String, String> getFileInfo(MP3FileWrapper f1) {
		SortedMap<String, String> info = new TreeMap<String, String>();

		info.put("FileHash", 		f1.getHexFileHash());
		info.put("ContentHash", 	f1.getHexContentHash());
		info.put("FileLength", 		Long.toString(f1.getFile().length()));
		info.put("Last Modified", 	Long.toString(f1.getFile().lastModified()));
		info.put("Version", 		f1.getVersion());
		info.put("Bitrate", 		Integer.toString(f1.getBitrate()));
		info.put("Seconds", 		Long.toString(f1.getLengthInSeconds()));
		info.put("XingOffset", 		Integer.toString(f1.getXingOffset()));
		info.put("BitLength", 		Integer.toString(f1.getBitLength()));
		info.put("FrameCount", 		Integer.toString(f1.getFrameCount()));
		info.put("XingBitrate", 	Integer.toString(f1.getXingBitrate()));
		info.put("ChannelMode", 	f1.getChannelMode());
		info.put("getEmphasis", 	f1.getEmphasis());
		info.put("getModeExtension", f1.getModeExtension());
		info.put("SampleRate", 		Integer.toString(f1.getSampleRate()));
		info.put("isOriginal", 		Boolean.toString(f1.isOriginal()));
		info.put("isVbr", 			Boolean.toString(f1.isVbr()));
		
		addID3v1Tags(f1.getId3v1Tag(), info);
		addID3v2Tags(f1.getId3v2Tag(), info);
		
		return info;
	}

	private static void addID3v1Tags(ID3v1 tag1, SortedMap<String, String> info) {
		String prefix = "ID3v1_";
		info.put(prefix + "Artist", (null == tag1) ? "NULL" : tag1.getArtist());
		info.put(prefix + "Title", (null == tag1) ? "NULL" : tag1.getTitle());
		info.put(prefix + "Album", (null == tag1) ? "NULL" : tag1.getAlbum());
		info.put(prefix + "Track", (null == tag1) ? "NULL" : tag1.getTrack());
		info.put(prefix + "Year", (null == tag1) ? "NULL" : tag1.getYear());
		info.put(prefix + "Genre", (null == tag1) ? "NULL" : tag1.getGenreDescription());
		info.put(prefix + "Comment", (null == tag1) ? "NULL" : tag1.getComment());
		info.put(prefix + "Version", (null == tag1) ? "NULL" : tag1.getVersion());
	}

	private static void addID3v2Tags(ID3v2 tag1, SortedMap<String, String> info) {
		String prefix = "ID3v2_";

		info.put(prefix + "Artist", (null == tag1) ? "NULL" : tag1.getArtist());
		info.put(prefix + "Title", (null == tag1) ? "NULL" : tag1.getTitle());
		info.put(prefix + "Album", (null == tag1) ? "NULL" : tag1.getAlbum());
		info.put(prefix + "Track", (null == tag1) ? "NULL" : tag1.getTrack());
		info.put(prefix + "Year", (null == tag1) ? "NULL" : tag1.getYear());
		info.put(prefix + "Genre", (null == tag1) ? "NULL" : tag1.getGenreDescription());
		info.put(prefix + "Comment", (null == tag1) ? "NULL" : tag1.getComment());
		info.put(prefix + "Version", (null == tag1) ? "NULL" : tag1.getVersion());
		info.put(prefix + "Composer", (null == tag1) ? "NULL" : tag1.getComposer());
		info.put(prefix + "Publisher", (null == tag1) ? "NULL" : tag1.getPublisher());
		info.put(prefix + "OrigArtist", (null == tag1) ? "NULL" : tag1.getOriginalArtist());
		info.put(prefix + "AlbumArtist", (null == tag1) ? "NULL" : tag1.getAlbumArtist());
		info.put(prefix + "Copyright", (null == tag1) ? "NULL" : tag1.getCopyright());
		info.put(prefix + "URL", (null == tag1) ? "NULL" : tag1.getUrl());
		info.put(prefix + "Encoder", (null == tag1) ? "NULL" : tag1.getEncoder());
		info.put(prefix + "ItunesComment", (null == tag1) ? "NULL" : tag1.getItunesComment());
		info.put(prefix + "Padding",  (null == tag1) ? "NULL" : Boolean.toString(tag1.getPadding()));
		info.put(prefix + "HasFooter", (null == tag1) ? "NULL" : Boolean.toString(tag1.hasFooter()));
		info.put(prefix + "HasUnsynchronisation", (null == tag1) ? "NULL" : Boolean.toString(tag1.hasUnsynchronisation()));
		info.put(prefix + "DataLength", (null == tag1) ? "NULL" : Integer.toString(tag1.getDataLength()));
		info.put(prefix + "Length", (null == tag1) ? "NULL" : Integer.toString(tag1.getLength()));
		info.put(prefix + "getObseleteFormat", (null == tag1) ? "NULL" : Boolean.toString(tag1.getObseleteFormat()));

	}

//	private staticinfo.putValues(String info.putString attrib, String val1) {
//		if(null == val1) val1 = "NULL";
//		System.out.println(prefix + " " + attrib + ": \"" + val1);
//	}

	private static String escapeFilename(String filename) {
		String escaped = filename.replaceAll(" ", "\\\\ ");
		escaped = escaped.replaceAll("#", "\\\\#");
		escaped = escaped.replaceAll("@", "\\\\@");
		escaped = escaped.replaceAll("<", "\\\\<");
		escaped = escaped.replaceAll(">", "\\\\>");
		escaped = escaped.replaceAll("\\{", "\\\\\\{");
		escaped = escaped.replaceAll("\\}", "\\\\\\}");
		escaped = escaped.replaceAll("\\[", "\\\\\\[");
		escaped = escaped.replaceAll("\\]", "\\\\\\]");
		escaped = escaped.replaceAll("\\(", "\\\\\\(");
		escaped = escaped.replaceAll("\\)", "\\\\\\)");
		escaped = escaped.replaceAll("\\$", "\\\\\\$");
		escaped = escaped.replaceAll("\\+", "\\\\\\+");
		escaped = escaped.replaceAll("%", "\\\\%");
		escaped = escaped.replaceAll("!", "\\\\!");
		escaped = escaped.replaceAll("`", "\\\\`");
		escaped = escaped.replaceAll("&", "\\\\&");
		escaped = escaped.replaceAll("\\*", "\\\\\\*");
		escaped = escaped.replaceAll("'", "\\\\'");
		escaped = escaped.replaceAll("\\|", "\\\\\\|");
		escaped = escaped.replaceAll("\\?", "\\\\\\?");
		escaped = escaped.replaceAll("\"", "\\\\\"");
		escaped = escaped.replaceAll("=", "\\\\=");
		escaped = escaped.replaceAll(":", "\\\\:");
		return escaped;
	}

	/**
	 * Recursively walk a directory tree and return a List of all Files found;
	 * the List is sorted using File.compareTo().
	 * 
	 * @param aStartingDir
	 *            is a valid directory, which can be read.
	 */
	static public List<File> getFileListing(File aStartingDir)
			throws FileNotFoundException {
		validateDirectory(aStartingDir);
		List<File> result = getFileListingNoSort(aStartingDir);
		Collections.sort(result);
		return result;
	}

	// PRIVATE //
	static private List<File> getFileListingNoSort(File aStartingDir)
			throws FileNotFoundException {
		List<File> result = new ArrayList<File>();
		File[] filesAndDirs = aStartingDir.listFiles();
		List<File> filesDirs = Arrays.asList(filesAndDirs);
		for (File file : filesDirs) {
			result.add(file); // always add, even if directory
			if (!file.isFile()) {
				// must be a directory
				// recursive call!
				List<File> deeperList = getFileListingNoSort(file);
				result.addAll(deeperList);
			}
		}
		return result;
	}

	/**
	 * Directory is valid if it exists, does not represent a file, and can be
	 * read.
	 */
	static private void validateDirectory(File aDirectory)
			throws FileNotFoundException {
		if (aDirectory == null) {
			throw new IllegalArgumentException("Directory should not be null.");
		}
		if (!aDirectory.exists()) {
			throw new FileNotFoundException("Directory does not exist: "
					+ aDirectory);
		}
		if (!aDirectory.isDirectory()) {
			throw new IllegalArgumentException("Is not a directory: "
					+ aDirectory);
		}
		if (!aDirectory.canRead()) {
			throw new IllegalArgumentException("Directory cannot be read: "
					+ aDirectory);
		}
	}
}