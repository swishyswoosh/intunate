package com.quietwisdom.intunate;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

import org.apache.commons.cli.*;
import org.json.simple.JSONValue;

import com.google.common.collect.*;
import com.gps.itunes.lib.exceptions.LibraryParseException;
import com.gps.itunes.lib.exceptions.NoChildrenException;
import com.gps.itunes.lib.items.tracks.*;
// import com.gps.itunes.lib.parser.main.app.utils.PropertyManager;
import com.gps.itunes.lib.tasks.LibraryParser;
import com.gps.itunes.lib.types.LibraryObject;
import com.gps.itunes.lib.xml.XMLParser;
import com.mpatric.mp3agic.*;


public final class Intunate {

	public static final String DEFAULT_DELIMITER = "\t";
	public static final String NULL_STRING = "";
	public static final String CONST_MUSIC_SPLIT_REGEX = "(?i)/Music/";


	public static void main(String[] args) throws Exception {
		
		Map<String, Object> conf = parseCommandLine(args);

		File startingDirectory = (File)conf.get("startingDirectory");
		String libraryFileLocation = (String)conf.get("libraryFileLocation");
		String outFileName = (String)conf.get("outFileName");
		boolean json_format = (Boolean)conf.get("json_format");
		String delim = (String)conf.get("delimiter");
		
		
		// libraryFileLocation = PropertyManager.getProperties().getProperty("libraryFileLocation");
		System.out.println("Scanning iTunes Library @ " + libraryFileLocation);
		List<String> missing = new ArrayList<String>();
		Map<String, Track> libTunes = new TreeMap<String, Track>();
		if(null != libraryFileLocation) 
			readITunesLibrary(libraryFileLocation, libTunes, missing);
		System.out.println("\tfound " + libTunes.keySet().size() + " records");

		System.out.println("Missing files referenced in iTunes...");
		Collections.sort(missing);
		for(String s : missing) System.out.println(s);
		System.out.println();
		System.out.println();
		

		System.out.println("Files referenced in iTunes...");
		for(String s : libTunes.keySet()) System.out.println(s);
		System.out.println();
		System.out.println();
		

		Set<String> libTunesUC = new TreeSet<String>();
		for(String s : libTunes.keySet()) 
			libTunesUC.add(s.toUpperCase());



		PrintWriter pw = new PrintWriter(new FileWriter(outFileName));
		boolean header = true;
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		List<String> imports = new ArrayList<String>();
		List<String> duplicates = new ArrayList<String>();
		Map<String, String> renames = new HashMap<String, String>();
		// Multimap<String,Object> rens = ArrayListMultimap.create();
		
		System.out.println("Scanning files...");
		List<File> files = getFileListing(startingDirectory);
		for(File file : files) {
			if(file.isDirectory()) continue;
			if(!file.toString().matches(".*\\.([mM][pP]3)$")) {
				// System.err.println("Skipping non-mp3 file: " + file.toString());
				continue;
			}
			if(!file.exists() || !file.canRead()) {
				System.err.println("Cannot find/read file: " + file.toString());
				continue;
			}
			
			md.reset();

			String escapedFilename = escapeFilename(file.toString());
//			System.out.format("File: %s\t%d\n", file.toString(), file.length());
//			System.out.println("File: " + escapedFilename);
			Mp3File mp3 = new Mp3File(file.toString());
			MP3FileWrapper fw = new MP3FileWrapper(file, mp3, md);

			Map<String, String> info = getFileInfo(fw);
			String relPath = getRelPath(file.toString());
			
			boolean iTunesFound = false;
			if(libTunes.size() > 0) {
				// System.out.println("Searching iTunes for " + relPath);
				// iTunesFound = libTunes.containsKey(relPath.toUpperCase());
				iTunesFound = libTunes.containsKey(relPath);
				if(!iTunesFound) {
//					System.out.println("# Not Found in iTunes: " + relPath);
//					System.out.println("# Not Found in iTunes UC: " + relPath.toUpperCase());
//					System.out.println("# Encoded: >>" + file.toURI());

					
					// Search for some possible alternate namings
					String iTunesRelPath = null;

					boolean altfound = false;
					int pos = relPath.lastIndexOf('.');
					for(int altNum = 1; altNum < 10; ++altNum) {
						iTunesRelPath = relPath.substring(0, pos) + " " + altNum + relPath.substring(pos);
//						if(libTunes.containsKey(iTunesRelPath.toUpperCase())) {
						if(libTunes.containsKey(iTunesRelPath)) {
							altfound = true;
							break;
						}
					}
					
					if(altfound) {
						// Check that iTunes file version exists...
						String iTunesMusicDirPath = getMusicDirPath(file.toString());
						String iTunesFilePath = iTunesMusicDirPath + iTunesRelPath;
						if(! (new File(iTunesFilePath)).exists()) {
							renames.put(escapedFilename, escapeFilename(iTunesFilePath));
						} else {
							// check altfile to ensure same files
							if(areSameMP3Files(new Mp3File(iTunesFilePath), mp3)) {
								duplicates.add(escapedFilename);
								continue;
							}
						}
					} else {
						if(! libTunesUC.contains(relPath.toUpperCase())) {
							System.out.println("File not found in library: " + file.toString());
							imports.add(escapedFilename);
						}
					}	
				}
			}
			info.put("Filename", file.toString());
			info.put("EscapedFilename", escapedFilename);
			info.put("FileLength", Long.toString(file.length()));
			info.put("iTunesFound", Boolean.toString(iTunesFound));
			
			
//			Track iTunesInfo = libTunes.get(file.toString().toUpperCase());
			Track iTunesInfo = libTunes.get(file.toString());
			if(null != iTunesInfo) {
				// String iTunesInfoString = iTunesInfo.getAllAdditionalInfo();
				// TODO
			}
			
			if(json_format) {
				// json:
				String jsonText = JSONValue.toJSONString(info);
				pw.println(jsonText);
				// System.out.println(jsonText);
			} else {
				// delimited:
				printInfoDelimited(info, pw, delim, header);
				if(header) header = false;
			}
			
			// float pctDone = i/files.size() * 100;
			// updateProgress(pctDone);
			// System.out.format("# File Processed: %s\n", info.get("EscapedFilename"));
		}
		System.out.println();
		
		System.out.println("# Need to import the following...");
		for(String s : imports)
			System.out.println("mv -nt ~/import/ \t" + s);
		
		for(String s : duplicates)
			System.out.println("rm -f " + s);

		for(String k : renames.keySet())
			System.out.println("mv -n " + k + " " + renames.get(k));

		
		if(null != pw) pw.close();
	}

	private static String getMusicDirPath(String filename) {
		if(null == filename) return null;

		String[] pair = filename.split(CONST_MUSIC_SPLIT_REGEX, 2);
		if(pair.length < 2) return null;
		
		// Walk past the prefix dir (one more dir) using path separator
		int pos = filename.indexOf(File.pathSeparatorChar, pair[0].length()+1);
		String prefixDirPath = filename.substring(0, pos+1);
		return prefixDirPath;
	}

	private static Map<String, Track> readITunesLibrary(String libFile, Map<String, Track> libTunes, List<String> orphans) {
		try {
			LibraryObject lo = new XMLParser().parseXML(libFile);
			LibraryParser lp = new LibraryParser(lo);
			Track[] tracks = lp.getAllTracks();
			int i = 0;
			for(;i < tracks.length; ++i) {
				String location = urlDecode(tracks[i].getLocation());
				if(location.matches(".*\\.([mM][pP]3)$")) {
					String relPath = getRelPath(location);
					if(null == relPath) {
						if(!location.contains("/Audiobooks/")) {
							orphans.add(location);
						}
						continue;
					} else {
//						libTunes.put(relPath.toUpperCase(), tracks[i]);
						libTunes.put(relPath, tracks[i]);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Unable to read iTunes library");
			// throw new RuntimeException(e);
			libTunes.clear();
		}
		return libTunes;
	}

	private static Map<String, Object> parseCommandLine(String[] args) {
		Map<String, Object> params = new HashMap<String, Object>();
		try {
			Options opts = new Options();
			opts.addOption("t", "delimiter", true, "(Optional) delimiter for output [default is tab]");
			opts.addOption("o", "outfile", true, "(Required) output filename");
			opts.addOption("d", "directory", true, "(Required) input directory path (enclose in quotes if spaces are in path, e.g: \"/path/to/My Music/\")");
			opts.addOption("l", "library", true, "(Required) iTunes XML library file to read");
			opts.addOption("j", "json_format", false, "(Optional) format output as JSON");
						
			CommandLineParser parser = new GnuParser();
			CommandLine cmd = parser.parse(opts, args);
			
			String delim = DEFAULT_DELIMITER;
			if(cmd.hasOption("t")) {
				String sep = cmd.getOptionValue("t");
				if(null != sep) delim = sep;
			}
			params.put("delimiter", delim);

			params.put("json_format", new Boolean(cmd.hasOption("j")));

			
			File startingDirectory = null;
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
			params.put("startingDirectory", startingDirectory);

			
			String libraryFileLocation = null;
			if(cmd.hasOption("l")) {
				libraryFileLocation = cmd.getOptionValue("l");

				File libraryFile = new File(libraryFileLocation);
				if(!libraryFile.isFile() || !libraryFile.exists() || !libraryFile.canRead()) {
					System.err.println("iTunes library file not found: " + libraryFileLocation);
					usage(opts);
				}
			} else {
				usage(opts);
			}
			params.put("libraryFileLocation", libraryFileLocation);


			String outFileName = null;
			if(cmd.hasOption("o")) {
				outFileName = cmd.getOptionValue("o");
			}
			if(null == outFileName) usage(opts);
			params.put("outFileName", outFileName);
			

		} catch( ParseException exp ) {
		    System.out.println( "Unexpected exception:" + exp.getMessage() );
		    System.exit(1);
		}
		return params;
	}

	private static String getRelPath(String location) {
		if(null == location) return null;

		String[] pair = location.split(CONST_MUSIC_SPLIT_REGEX, 2);
		if(pair.length < 2) return null;
		return pair[1];
	}

	static void updateProgress(double progressPercentage) {
		final int width = 50; // progress bar width in chars

		System.out.print("\r[");
		int i = 0;
		for (; i <= (int) (progressPercentage * width); i++) {
			System.out.print(".");
		}
		for (; i < width; i++) {
			System.out.print(" ");
		}
		System.out.print("]");
	}

	private static boolean areSameMP3Files(Mp3File m1, Mp3File m2) {
		return(m1.getBitrate() == m2.getBitrate() && m1.getLengthInSeconds() == m2.getLengthInSeconds());
	}

	private static String urlDecode(String url) throws UnsupportedEncodingException, URISyntaxException {
		if(null == url) return null;
		String filename = new URI(url).getPath();
		return filename;
	}

	private static void usage(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "Intunate", opts );
		System.exit(0);
	}

	private static void printInfoDelimited(Map<String, String> info, PrintWriter pw, String delim, boolean addHeader) {
		if(addHeader) {
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
			pw.print("\"" + val + "\"");
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
		info.put(prefix + "Artist", (null == tag1) ? null : tag1.getArtist());
		info.put(prefix + "Title", (null == tag1) ? null : tag1.getTitle());
		info.put(prefix + "Album", (null == tag1) ? null : tag1.getAlbum());
		info.put(prefix + "Track", (null == tag1) ? null : tag1.getTrack());
		info.put(prefix + "Year", (null == tag1) ? null : tag1.getYear());
		info.put(prefix + "Genre", (null == tag1) ? null : tag1.getGenreDescription());
		info.put(prefix + "Comment", (null == tag1) ? null : tag1.getComment());
		info.put(prefix + "Version", (null == tag1) ? null : tag1.getVersion());
	}

	private static void addID3v2Tags(ID3v2 tag1, SortedMap<String, String> info) {
		String prefix = "ID3v2_";

		info.put(prefix + "Artist", (null == tag1) ? null : tag1.getArtist());
		info.put(prefix + "Title", (null == tag1) ? null : tag1.getTitle());
		info.put(prefix + "Album", (null == tag1) ? null : tag1.getAlbum());
		info.put(prefix + "Track", (null == tag1) ? null : tag1.getTrack());
		info.put(prefix + "Year", (null == tag1) ? null : tag1.getYear());
		info.put(prefix + "Genre", (null == tag1) ? null : tag1.getGenreDescription());
		info.put(prefix + "Comment", (null == tag1) ? null : tag1.getComment());
		info.put(prefix + "Version", (null == tag1) ? null : tag1.getVersion());
		info.put(prefix + "Composer", (null == tag1) ? null : tag1.getComposer());
		info.put(prefix + "Publisher", (null == tag1) ? null : tag1.getPublisher());
		info.put(prefix + "OrigArtist", (null == tag1) ? null : tag1.getOriginalArtist());
		info.put(prefix + "AlbumArtist", (null == tag1) ? null : tag1.getAlbumArtist());
		info.put(prefix + "Copyright", (null == tag1) ? null : tag1.getCopyright());
		info.put(prefix + "URL", (null == tag1) ? null : tag1.getUrl());
		info.put(prefix + "Encoder", (null == tag1) ? null : tag1.getEncoder());
		info.put(prefix + "ItunesComment", (null == tag1) ? null : tag1.getItunesComment());
		info.put(prefix + "Padding",  (null == tag1) ? null : Boolean.toString(tag1.getPadding()));
		info.put(prefix + "HasFooter", (null == tag1) ? null : Boolean.toString(tag1.hasFooter()));
		info.put(prefix + "HasUnsynchronisation", (null == tag1) ? null : Boolean.toString(tag1.hasUnsynchronisation()));
		info.put(prefix + "DataLength", (null == tag1) ? null : Integer.toString(tag1.getDataLength()));
		info.put(prefix + "Length", (null == tag1) ? null : Integer.toString(tag1.getLength()));
		info.put(prefix + "getObseleteFormat", (null == tag1) ? null : Boolean.toString(tag1.getObseleteFormat()));

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