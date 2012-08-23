package updaters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.UUID;
import java.util.regex.Pattern;

import play.Logger;
import play.libs.WS;

public class FileFeedStorer implements FeedStorer {
	private String path;
	
	private static Pattern uuidRe = Pattern.compile("^[0-9a-fA-F\\-]+$");
	
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	@Override
	public String storeFeed(String url) {
		Logger.info("Downloading feed %s", url);
		
		String id = UUID.randomUUID().toString();
		
		// http://stackoverflow.com/questions/921262
		URL parsed;
		try {
			parsed = new URL(url);
		} catch (MalformedURLException e1) {
			Logger.error("bad url %s", url);
			return null;
		}
		
		ReadableByteChannel rbc;
		try {
			rbc = Channels.newChannel(parsed.openStream());
		} catch (IOException e1) {
			Logger.error("IO exception retrieving %s", url);
			return null;
		}
		
		FileOutputStream out;
		try {
			out = new FileOutputStream(path + "/" + id);
		} catch (FileNotFoundException e) {
			Logger.error("Cannot open file for writing: %s", path + "/" + id);
			return null;
		}
		
		try {
			out.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
		} catch (IOException e) {
			Logger.error("IO exception saving %s", url);
			try {
				out.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				Logger.error("Cannot close file,  leaving unclosed");
			}
			return null;
		}
		
		try {
			out.close();
		} catch (IOException e) {
			Logger.error("Cannot close file,  leaving unclosed");
			return null;
		}
		
		Logger.info("done");
		return id;
	}

	@Override
	public File getFeed(String feedId) {
		// make sure it looks like a UUID, so we don't have something like
		// ../../../etc/passwd
		if (!uuidRe.matcher(feedId).matches())
			return null;
		
		return new File(path + "/" + feedId);
	}

	public void releaseFeed (String feedId) {};
}
