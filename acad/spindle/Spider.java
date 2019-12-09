package com.bitmechanic.spindle;

/*
 * This file is a derived version of a file from Spindle, (c) bitmechanic.com.
 * 
 * This version, based on Spindle 0.90, with fixes and enhancements added 
 * by Richard Dallaway.
 * 
 * Spindle is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * Spindle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Spindle; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Security;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;


import cvu.html.HTMLTokenizer;
import cvu.html.TagToken;
import cvu.html.TextToken;

/**
 * Provides a HTTPD crawler which builds a Lucene index for searching.
 * 
 * @version $Revision: 1.3 $ $Date: 2004/06/20 21:34:51 $
 */
public class Spider implements Runnable
{

	private static String lineSep = System.getProperty("line.separator");

	private String indexDir;
	private ArrayList urls;
	private ArrayList include;
	private ArrayList exclude;
	private ArrayList threadList;

	/** List (possibly zero length) of tags to include in the page description. */
	private ArrayList descriptionTags;
	
	/** Treat documents contanining a name sections as separate documents in the index. */
	private boolean eachNameIsADocument;

	/** The pattern to look for when splitting a document based on <a name='...'> tags. */
	private final Pattern aNamePattern = Pattern.compile("a name=(\"|')([^'\" ]+)", Pattern.CASE_INSENSITIVE);
	
	private boolean verbose;
	private boolean incremental;

	private boolean groksHTTPS;

	private IndexWriter index;
	private HashMap indexedURLs;
	private HashMap mimeTypes;


	private int threads;
	private int descSize;

	private int bytes;

	public static void main(String argv[]) throws Exception
	{
		Spider s = new Spider(argv);
		s.go();
	}

	public Spider(String argv[])
	{
		groksHTTPS = true;
		verbose = false;
		incremental = false;
		eachNameIsADocument = false;
		threads = 2;
		descSize = 256;
		bytes = 0;
		include = new ArrayList();
		exclude = new ArrayList();
		urls = new ArrayList();
		threadList = new ArrayList();
		descriptionTags = new ArrayList();
		indexedURLs = new HashMap();
		mimeTypes = new HashMap();
		parseArgs(argv);
	}

	public void go() throws Exception
	{
		// create the index directory -- or append to existing
		if (verbose)
		{
			print("Creating index in: " + indexDir);
			if (incremental)
				print("    - using incremental mode");
		}
		index =
			new IndexWriter(
				new File(indexDir),
				new StandardAnalyzer(),
				!incremental);

		// check if we can do https URLs
		try
		{
			System.setProperty(
				"java.protocol.handler.pkgs",
				"com.sun.net.ssl.internal.www.protocol");
			Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
			URL url = new URL("https://www.bitmechanic.com/");
		}
		catch (Exception e)
		{
			groksHTTPS = false;
			if (verbose)
				print("Disabling support for https URLs");
		}

		// index each entry point URL
		long start = System.currentTimeMillis();
		for (int i = 0; i < threads; i++)
		{
			Thread t = new Thread(this, "Spindle Spider Thread #" + (i + 1));
			t.start();
			threadList.add(t);
		}
		while (threadList.size() > 0)
		{
			Thread child = (Thread) threadList.remove(0);
			child.join();
		}
		long elapsed = System.currentTimeMillis() - start;

		// save the index
		if (verbose)
		{
			print(
				"Indexed "
					+ indexedURLs.size()
					+ " URLs ("
					+ (bytes / 1024)
					+ " KB) in "
					+ (elapsed / 1000)
					+ " seconds");
			print("Optimizing index");
		}
		index.optimize();
		index.close();
	}

	public void run()
	{
		String url;
		try
		{
			while ((url = dequeueURL()) != null)
			{
				indexURL(url);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		threads--;
	}

	public synchronized String dequeueURL() throws Exception
	{
		while (true)
		{
			if (urls.size() > 0)
			{
				return (String) urls.remove(0);
			}
			else
			{
				threads--;
				if (threads > 0)
				{
					wait();
					threads++;
				}
				else
				{
					notifyAll();
					return null;
				}
			}
		}
	}

	public synchronized void enqueueURL(String url)
	{
		if (indexedURLs.get(url) == null)
		{
			urls.add(url);
			indexedURLs.put(url, Boolean.TRUE);
			notifyAll();
		}
	}

	private void indexURL(String url) throws Exception
	{
		if (verbose)
			print(
				"  "
					+ Thread.currentThread().getName()
					+ ": Adding URL: "
					+ url);
		final URLSummary[] summaryList = loadURL(url);

		if (summaryList != null)
		{
		    
		    for( int s=0, n=summaryList.length; s<n; s++)
		    {
		    
		        final URLSummary summary = summaryList[s];
				String urls[] = parseURLs(summary);
	
				Document doc = new Document();
				doc.add(Field.UnIndexed("url", summary.url.toExternalForm()));
				doc.add(Field.UnIndexed("title", summary.title));
				doc.add(Field.UnIndexed("desc", summary.desc));
				doc.add(Field.Text("body", summary.body));
				synchronized (this)
				{
					bytes += summary.body.length();
					index.addDocument(doc);
				}
	
				for (int i = 0; i < urls.length; i++)
				{
					// check against the include/exclude list
					boolean add = true;
					for (int x = 0; add && x < include.size(); x++)
					{
						String inc = (String) include.get(x);
						add = (urls[i].indexOf(inc) != -1);
					}
					for (int x = 0; add && x < exclude.size(); x++)
					{
						String ex = (String) exclude.get(x);
						add = (urls[i].indexOf(ex) == -1);
					}
	
					if (add)
					{
						enqueueURL(urls[i]);
					}
				}
			
		    } //end for each summary
		}
	}


	/**
	 * @param	tagName	The name of a tag to test (e.g., "p").
	 * @return True if the tag is to be included in a description; false otherwise.
	 */
	public boolean isDescriptionTag(String tagName)
	{
		return descriptionTags.contains(tagName);
	}

	/**
	 * Parse the contents of the HTML stored in the URL summary
	 * to extract links, title, and description of
	 * the page.  Note that this method is public just to make
	 * it easy to unit test.
	 * 
	 * @param	summary	 The page to parse, which is updated after this
	 * 					 call to have a description.
	 * @throws	IOException	Thrown if there's a problem reading the body
	 * 						of the page into the HTML tokenizer.
	 * @return A list of URLs (possibly zero length, but never null.
	 */
	public String[] parseURLs(final URLSummary summary) throws IOException
	{
		StringBuffer desc = new StringBuffer();
		ArrayList urls = new ArrayList();

		// Flag indicating if we're in a tag that should be included in the description.		
		boolean inDescriptionTag = false;
	
		// Has the user set any descriptions tags?
		boolean watchDescriptionTags = (descriptionTags.size() > 0);
	

		HTMLTokenizer ht = new HTMLTokenizer(new StringReader(summary.body));
		for (Enumeration e = ht.getTokens(); e.hasMoreElements();)
		{
			Object obj = e.nextElement();
			if (obj instanceof TagToken)
			{
				TagToken tag = (TagToken) obj;
				String tagName = tag.getName().toLowerCase();
				
				
				if (watchDescriptionTags && isDescriptionTag(tagName))
				{
					inDescriptionTag = !tag.isEndTag();
				}

				String url = null;
				
				if (tagName.equals("a"))
				{
					url = tag.getAttributes().get("href");
				}
				else if (tagName.equals("frame"))
				{
					url = tag.getAttributes().get("src");
				}
				else if (tagName.equals("title") && e.hasMoreElements() && !tag.isEndTag())
				{
					obj = e.nextElement();
					if (obj instanceof TextToken)
					{
						TextToken title = (TextToken) obj;
						summary.title = title.getText();
					}
				}

				if (url != null)
				{
					if (url.startsWith("http://") || (url.startsWith("https://") && groksHTTPS))
					{

						// verify we're on the same host and port
						URL u = new URL(url);
						if (u.getHost().equals(summary.url.getHost()) && u.getPort() == summary.url.getPort())
						{

							url = chopOffNamedAnchor(url);
							if (indexedURLs.get(url) == null)
								urls.add(url);
						}
					}
					else if ( url.indexOf("://") == -1
							&& !url.startsWith("mailto:")
							&& !url.startsWith("#")
							&& !url.startsWith("javascript:"))
					{
						// parse relative url
						url = formURL(summary.url, url);
						url = chopOffNamedAnchor(url);
						if (indexedURLs.get(url) == null)
							urls.add(url);
					}
				}
			}
			else if ((obj instanceof TextToken) && (inDescriptionTag || !watchDescriptionTags))
			{
				// ... the !watchDescriptionTags part returns this code to default behaviour of using
				// all text as part of the description
				TextToken t = (TextToken) obj;
				String text = t.getText();
				if (text != null && text.trim().length() > 0)
					desc.append(text.trim()).append(" ");
			}
			
		}

		if (desc.length() > descSize)
			desc.setLength(descSize);
		summary.desc = desc.toString();

		String list[] = new String[urls.size()];
		urls.toArray(list);
		return list;
	}

	private String chopOffNamedAnchor(final String url)
	{
		int pos = url.indexOf("#");
		if (pos == -1)
			return url;
		else
			return url.substring(0, pos);
	}

	// converts relative URL to absolute URL
	private String formURL(final URL origURL, String newURL)
	{
		StringBuffer base = new StringBuffer(origURL.getProtocol());
		base.append("://").append(origURL.getHost());
		if (origURL.getPort() != -1)
		{
			base.append(":").append(origURL.getPort());
		}

		if (newURL.startsWith("/"))
		{
			base.append(newURL);
		}
		else if (newURL.startsWith(".."))
		{
			String file = origURL.getFile();
		}
		else
		{
			String file = origURL.getFile();
			int pos = file.lastIndexOf("/");
			if (pos != -1)
				file = file.substring(0, pos);

			while (newURL.startsWith("../"))
			{
				pos = file.lastIndexOf("/");
				file = file.substring(0, pos);
				newURL = newURL.substring(3);
			}

			base.append(file).append("/").append(newURL);
		}

		return base.toString();
	}

	private URLSummary[] loadURL(final String url) throws Exception
	{
		final URL u = new URL(url);
		HttpURLConnection uc = null;
		String ct = "";
		try
		{
			uc = (HttpURLConnection) u.openConnection();
			uc.setAllowUserInteraction(false);
			if (uc.getResponseCode() != 200)
				return null;
			ct = uc.getContentType();
		}
		catch (FileNotFoundException e)
		{
			// 404
		    print("Unexpected status code="+uc.getResponseCode()+" for "+url);
			return null;
		}

		// RZD: the mime type could contain the page encoding, such as "text/html;charset=ISO-8859-1"
		// and we're only interested in the part before the semicolon.
		
		int semiColon = ct.indexOf(';');
		if (semiColon != -1)
		{
		    ct = ct.substring(0, semiColon);
		}
		
		if (mimeTypes.get(ct) == null)
		{
			return null;
		}
		
		BufferedReader in =
			new BufferedReader(new InputStreamReader(uc.getInputStream()));

		StringBuffer body = new StringBuffer(2048);
		String line;

		while ((line = in.readLine()) != null)
		{
			body.append(line);
			body.append(lineSep);
		}
		in.close();
		
		
	    URLSummary summary = new URLSummary();
	    summary.url = u;
	    summary.body = body.toString();
		
		
		List summaries = new ArrayList();
		
		// We either treat the page as a single document to index or, at the user's request,
		// split the page into many documents, where each document is defined by an <a name> tag.

		// Note the quick-and-dirty check for an <a name> tag :-(
		if (eachNameIsADocument == false || summary.body.indexOf("<a name") == -1)
		{ 
		    summaries.add(summary);
		}
		else
		{
		    // Split the page up into many documents:
		    
		    // We're looking for <a name>:  we might want to think about folding
		    // this into the HTML tokenization process somehow.
		    Matcher matcher = aNamePattern.matcher(summary.body);
		    
		    int positionInDocument = 0; // the first fragment starts at the top of the document
		    String name = null; // the first fragment has no name, because it has no <a name> tag.
		    
		    while (matcher.find())
		    {
		        // Where the match was found
		        int startOfMatch = matcher.start(); 
		       
		        print("  Forming document fragment #"+name);
		        
		        final URLSummary fragment = new URLSummary();
		        if (name == null)
		        {
		            fragment.url = summary.url;
		        }
		        else
		        {
		            fragment.url = new URL(summary.url.toExternalForm() + "#" + name);
		        }
		        
		        // This fragment runs from where-we've-read-to up to the <a name> tag
		        // we've found:
		        fragment.body = summary.body.substring(positionInDocument, startOfMatch);
		        summaries.add(fragment);
		        
		        //	The next fragment body will start from here...
		        positionInDocument = startOfMatch; 
		        
		        // ...and will have this name:
		        name = matcher.group(2); // groups are indexed from 1
		        
		    }
		    
		    // Finally fragement-ize from the end of the last fragment to the end of the document:
		    print("  Forming document fragment #"+name);
		    final URLSummary fragment = new URLSummary();
	        fragment.url = new URL(summary.url.toExternalForm() + "#" + name);
	        fragment.body = summary.body.substring(positionInDocument); // to the end
	        summaries.add(fragment);
		        
		}
		
		
		return (URLSummary[])summaries.toArray(new URLSummary[summaries.size()]);
		
	}

	private void parseArgs(String argv[])
	{

		for (int i = 0; i < argv.length; i++)
		{
			if (argv[i].equals("-u"))
				urls.add(argv[++i]);
			else if (argv[i].equals("-d"))
				indexDir = argv[++i];
			else if (argv[i].equals("-i"))
				include.add(argv[++i]);
			else if (argv[i].equals("-e"))
				exclude.add(argv[++i]);
			else if (argv[i].equals("-v"))
				verbose = true;
			else if (argv[i].equals("-a"))
				incremental = true;
			else if (argv[i].equals("-m"))
				mimeTypes.put(argv[++i], Boolean.TRUE);
			else if (argv[i].equals("-t"))
				threads = Integer.parseInt(argv[++i]);
			else if (argv[i].equals("-s"))
				descSize = Integer.parseInt(argv[++i]);
			else if (argv[i].equals("-dt"))
			{
				String tag = argv[i+1];
				if (tag != null)
				{
					descriptionTags.add(tag.toLowerCase());
				}
			}
			else if (argv[i].equals("-n"))
			{
			    eachNameIsADocument = true;
			    System.out.println("<a name> splitting is on");
			}

		}


		if (urls.size() == 0)
			throw new IllegalArgumentException("Missing required argument: -u [start url]");
		if (indexDir == null)
			throw new IllegalArgumentException("Missing required argument: -d [index dir]");

		if (threads < 1)
			throw new IllegalArgumentException(
				"Invalid number of threads: " + threads);

		if (mimeTypes.size() == 0)
		{
			// add default MIME types
			mimeTypes.put("text/html", Boolean.TRUE);
			mimeTypes.put("text/plain", Boolean.TRUE);
		}
	}

	private void print(String str)
	{
		System.out.println(str);
	}

}

