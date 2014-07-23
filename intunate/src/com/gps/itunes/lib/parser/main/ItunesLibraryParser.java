package com.gps.itunes.lib.parser.main;

import com.gps.itunes.lib.exceptions.LibraryParseException;

import java.io.IOException;

import com.gps.itunes.lib.exceptions.NoChildrenException;
import com.gps.itunes.lib.parser.main.app.utils.LogInitializer;
import com.gps.itunes.lib.parser.main.app.utils.MemoryCheck;
import com.gps.itunes.lib.parser.main.app.utils.PropertyManager;
import com.gps.itunes.lib.tasks.LibraryPrinter;
import com.gps.itunes.lib.types.LibraryObject;
import com.gps.itunes.lib.xml.XMLParser;

/**
 * Main class for this project.
 * 
 * @author Paulie
 * 
 */
public class ItunesLibraryParser {

	static {
		new LogInitializer();
	}

	public static void main(String args[]) {
		try {
			new ItunesLibraryParser();
		} catch (final NoChildrenException e) {
			log.error("NoChildrenException occurred", e);
		} catch (IOException e) {
			log.error(e);
		} catch (LibraryParseException e) {
			log.error(e);
		}
	}

	private static final org.apache.log4j.Logger log = org.apache.log4j.Logger
			.getLogger(ItunesLibraryParser.class);

	public ItunesLibraryParser() throws NoChildrenException, IOException,
			LibraryParseException {

		final LibraryObject root = new XMLParser().parseXML(PropertyManager
				.getProperties().getProperty("libraryFileLocation"));

		log.debug(root);

		MemoryCheck.printUsedMemoryInfo();

		final LibraryPrinter printer = new LibraryPrinter(root);
		 printer.printLibrary();
		 
		 checkMemory();
	}
	
	private void checkMemory(){
		MemoryCheck.printUsedMemoryInfo();
	}

}
