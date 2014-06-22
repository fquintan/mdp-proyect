package org.mdp.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.rmi.AlreadyBoundException;
import java.util.zip.GZIPInputStream;

import javax.management.openmbean.OpenDataException;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * Main method to index plain-text abstracts from DBpedia using Lucene.
 * 
 * @author Aidan
 */
public class IndexTitleAndAuthor {

	public enum FieldNames {
		ABSTRACT, AUTHOR, INDEX, MODIFIED, TITLE 
	}

	public static int TICKS = 10000;

	public static void main(String args[]) throws IOException, ClassNotFoundException, AlreadyBoundException, InstantiationException, IllegalAccessException{
		Option inO = new Option("i", "input file");
		inO.setArgs(1);
		inO.setRequired(true);

		Option ingzO = new Option("igz", "input file is GZipped");
		ingzO.setArgs(0);

		Option outO = new Option("o", "output index directory");
		outO.setArgs(1);

		Option helpO = new Option("h", "print help");

		Options options = new Options();
		options.addOption(inO);
		options.addOption(ingzO);
		options.addOption(outO);
		options.addOption(helpO);

		CommandLineParser parser = new BasicParser();
		CommandLine cmd = null;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.err.println("***ERROR: " + e.getClass() + ": " + e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("parameters:", options );
			return;
		}

		// print help options and return
		if (cmd.hasOption("h")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("parameters:", options );
			return;
		}

		String dir = cmd.getOptionValue("o");
		System.err.println("Opening directory at  "+dir);
		File fDir = new File(dir);
		if(fDir.exists()){
			if(fDir.isFile()){
				throw new IOException("Cannot open directory at "+dir+" since its already a file.");
			} 
		} else{
			if(!fDir.mkdirs()){
				throw new IOException("Cannot open directory at "+dir+". Try create the directory manually.");
			}
		}
		
		String in = cmd.getOptionValue(inO.getOpt());
		System.err.println("Opening input at  "+in);
		InputStream is = new FileInputStream(in);
		if(cmd.hasOption(ingzO.getOpt())){
			is = new GZIPInputStream(is);
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));

		indexTitleAndAuthor(br, fDir);

		br.close();
	}

	public static void indexTitleAndAuthor(BufferedReader input, File indexDir) throws IOException{
		/*Lucene bureaucracy*/
		Directory dir = FSDirectory.open(indexDir);
		Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_48);
		IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_48, analyzer);
		iwc.setOpenMode(OpenMode.CREATE);
		IndexWriter writer = new IndexWriter(dir, iwc);
		
		String line = null;
		int read = 0;
		int author = 0;
		String  titleLine = "#*",
				authorLine = "#@",
				yearLine = "#t",
				publicationLine = "#c",
				indexLine = "#index",
				citationLine = "#%",
				abstractLine = "#!";
				
		/*
		 * Now the good part, we're reading the text file, identifying the 
		 * relevant data within it and indexing it as Lucene documents, 
		 * we assume the following structure for the text file:
		 * #* --- paperTitle
		 * #@ --- Authors (Separated by commas)
		 * #t ---- Year
		 * #c  --- publication venue
		 * #index---- index id of this paper
		 * #% ---- the id of references of this paper (there are multiple lines, with each indicating a reference)
		 * #! --- Abstract
		 * */
		while ((line = input.readLine()) != null) {
			
			read++;
			String print = "";
			if (read % TICKS == 0) {
				System.err.println(read + " lines read");
			}
			line = line.trim();
			if(line.startsWith(titleLine)){
				Document doc = new Document();
				String title = line.substring(titleLine.length());
				Field titleField = new TextField(FieldNames.TITLE.name(), title, Field.Store.YES);
				doc.add(titleField);
				print += "t:"+title;
				if((line = input.readLine()).startsWith(authorLine)){
					author++;
//					String[] authors = line.trim().substring(authorLine.length()).split(",");
					String authors = line.trim().substring(authorLine.length()).replaceAll(",", " ");
					Field authorField = new TextField(FieldNames.AUTHOR.name(), authors, Field.Store.YES);
					doc.add(authorField);
					print += "a:"+authors;
				}
				if((line = input.readLine()).startsWith(yearLine)){}
				if((line = input.readLine()).startsWith(publicationLine)){}
				if((line = input.readLine()).startsWith(indexLine)){
					String index = line.trim().substring(indexLine.length());
					Field indexField= new StringField(FieldNames.INDEX.name(), index, Field.Store.YES);
					doc.add(indexField);
				}
				while((line = input.readLine()).startsWith(citationLine)){}
				if((line = input.readLine()).startsWith(abstractLine)){
					String absString = line.trim().substring(abstractLine.length());
					if(!absString.isEmpty()){
						Field abstractField = new StringField(FieldNames.ABSTRACT.name(), absString, Field.Store.YES);
						doc.add(abstractField);
					}
				}
				Field modified = new LongField(FieldNames.MODIFIED.name(), System.currentTimeMillis(), Field.Store.NO);
				doc.add(modified);
				writer.addDocument(doc);
				if (read % TICKS == 0) {
					System.err.println(print);
				}
			}
		}
		
		writer.close();
		System.err.println("Finished reading");
		System.out.println(author);
	}
}