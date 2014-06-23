package org.mdp.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.rmi.AlreadyBoundException;
import java.util.HashMap;

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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.mdp.cli.IndexTitleAndAuthor.FieldNames;

/**
 * Main method to search articles using Lucene.
 * 
 * @author Aidan
 */
public class SearchIndex {

	public static final HashMap<String,Float> BOOSTS = new HashMap<String,Float>();
	static {
		BOOSTS.put(FieldNames.ABSTRACT.name(), 1f); //<- default
		BOOSTS.put(FieldNames.TITLE.name(), 5f);
		BOOSTS.put(FieldNames.AUTHOR.name(), 5f);
		BOOSTS.put(FieldNames.INDEX.name(), 1f);
		BOOSTS.put(FieldNames.YEAR.name(), 1f); 
	}

	public static final int DOCS_PER_PAGE  = 10;

	public static void main(String args[]) throws IOException, ClassNotFoundException, AlreadyBoundException, InstantiationException, IllegalAccessException{
		Option inO = new Option("i", "input index directory");
		inO.setArgs(1);
		inO.setRequired(true);

		Options options = new Options();
		options.addOption(inO);

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

		String in = cmd.getOptionValue(inO.getOpt());
		System.err.println("Opening directory at  "+in);

		startSearchApp(in);
	}

	public static void startSearchApp(String in) throws IOException{
		//TODO Implement following the board :)
		IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(in)));
		IndexSearcher searcher = new IndexSearcher(reader);
		Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_48);
		
		MultiFieldQueryParser queryParser = new MultiFieldQueryParser(Version.LUCENE_48,
				new String[] {//FieldNames.ABSTRACT.name(),
							  FieldNames.TITLE.name(),
							  FieldNames.AUTHOR.name()
							  },
				analyzer,
				BOOSTS);
		queryParser.setDefaultOperator(queryParser.OR_OPERATOR);
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		
		try {
			while (true) {
				System.out.println("Enter keyword query please...");
				String line = br.readLine();
				if (line != null) {
					line = line.trim();
					if (!line.isEmpty()) {
						Query query = queryParser.parse(line);
						TopDocs results = searcher.search(query, DOCS_PER_PAGE);
						ScoreDoc[] hits = results.scoreDocs;
						System.out.println("Matching Documents: "+results.totalHits);
						for (int i = 0; i < hits.length; i++) {
							Document doc = searcher.doc(hits[i].doc);
							String title = doc.get(FieldNames.TITLE.name());
							String abst = doc.get(FieldNames.ABSTRACT.name());
							String author = doc.get(FieldNames.AUTHOR.name());
							String index = doc.get(FieldNames.INDEX.name());
							String year = doc.get(FieldNames.YEAR.name());
							System.out.println((i+1) + "\ttitle:" + title + 
									"\tauthor:" + author + 
//									"\t" + hits[i].score +
									"\tindex:" + index +
									"\tyear:"+ year +
									"\t" + abst);
						}
					}
				}
			}

		} catch (org.apache.lucene.queryparser.classic.ParseException e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	
	

}