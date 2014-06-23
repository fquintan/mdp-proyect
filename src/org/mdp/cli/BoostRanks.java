package org.mdp.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.rmi.AlreadyBoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * Main method to index plain-text abstracts from DBpedia using Lucene.
 * 
 * @author Aidan
 */
public class BoostRanks {

	public enum FieldNames {
		RANK
	}

	public static int TICKS = 10000;

	public static void main(String args[]) throws IOException, ClassNotFoundException, AlreadyBoundException, InstantiationException, IllegalAccessException{
		Option inO = new Option("i", "input ranks file");
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

		boostRanks(br, fDir);

		br.close();
	}

	public static void boostRanks(BufferedReader input, File indexDir) throws IOException{
		//@TODO following board
		Directory dir = FSDirectory.open(indexDir);
		Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_48);
		
		IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_48, analyzer);
		iwc.setOpenMode(OpenMode.APPEND);
		IndexWriter writer = new IndexWriter(dir, iwc);
		
		IndexReader reader = DirectoryReader.open(dir);
		
		Map<String, Double> ranks = new HashMap<String, Double>();
		
		String line = null;
		int read = 0;
		
		/*First we load the ranks into memory*/
		while ((line = input.readLine()) != null) {
			if(++read % TICKS == 0){
				System.err.println(read + " lines read");
			}
			line = line.trim();
			if(!line.isEmpty()){
				String[] tabs = line.split("\t");
				String paperIndex = tabs[0];
				double rank = Double.parseDouble(tabs[1]);
				ranks.put(paperIndex, rank);
			}
		}
		System.err.println("Finished reading "+read+" lines");
		
		/*Now for each document, we boost and update the rank*/
		read = 0;
		for (int i = 0; i < reader.maxDoc(); i++) {
			if(++read % TICKS == 0){
				System.err.println(read + " docs read");
			}
			Document doc = reader.document(i);
			IndexableField indexF = doc.getField(IndexTitleAndAuthor.FieldNames.INDEX.name());
			if(indexF != null){

				String paperIndex = indexF.stringValue();
				Double rankD = ranks.get(paperIndex);
				rankD = (rankD == null) ? 0D : rankD;

				float boost = getBoost(rankD);
				IndexableField title = doc.getField(IndexTitleAndAuthor.FieldNames.TITLE.name());
				((Field) title).setBoost(boost);

				Term indexT = new Term(IndexTitleAndAuthor.FieldNames.INDEX.name());
				writer.updateDocument(indexT, doc);
			}
		}
		System.err.println("Finished reading "+read+" docs");
		writer.close();
	}
	
	public static float getBoost(double rank){
		//@TODO following board
		float magicNumber = 100000;
		return ((float) rank * magicNumber) + 1;
	}
}