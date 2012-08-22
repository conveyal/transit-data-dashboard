package updaters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.UUID;
import java.util.regex.Pattern;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;

import play.Logger;
import play.libs.WS;

/**
 * Store feeds in S3
 * @author mattwigway
 */
public class S3FeedStorer implements FeedStorer {
	private String path;
	private AWSCredentials credentials;
	private String accessKey;
	private String secretKey;
	private AmazonS3 s3Client;
	private String bucket;
	
	public void setAccessKey (String accessKey) {
		this.accessKey = accessKey;
		buildCredentialsIfReady();
	}
	
	public void setSecretKey (String secretKey) {
		this.secretKey = secretKey;
		buildCredentialsIfReady();
	}
	
	public void setBucket (String bucket) {
		this.bucket = bucket;
	}
	
	private void buildCredentialsIfReady() {
		if (accessKey != null && secretKey != null) {
			credentials = new BasicAWSCredentials(accessKey, secretKey);
			s3Client = new AmazonS3Client(credentials);
		}
	}

	public S3FeedStorer () {
		this.accessKey = null;
		this.secretKey = null;
	}
	
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
		
		URL feed;
		try {
			feed = new URL(url);
		} catch (MalformedURLException e) {
			Logger.error("Bad URL %s", url);
			e.printStackTrace();
			return null;
		}
		
		URLConnection conn;
		try {
			conn = feed.openConnection();
		} catch (IOException e1) {
			Logger.error("IOException retrieving URL %s", url);
			e1.printStackTrace();
			return null;
		}
		
		ObjectMetadata meta = new ObjectMetadata();
		meta.setContentType("application/x-zip-compressed");
		// TODO check that this is valid
		meta.setContentLength(conn.getContentLength());
		
		InputStream feedStream;
		try {
			feedStream = conn.getInputStream();
		} catch (IOException e) {
			Logger.error("IOException retrieving URL %s", url);
			e.printStackTrace();
			return null;
		}
		
		// no need to save result, this will throw an error if it doesn't work
		this.s3Client.putObject(this.bucket, id, feedStream, meta);
		
		Logger.info("saved with id %s", id);
		return id;
	}

	@Override
	public File getFeed(String feedId) {
		// make sure it looks like a UUID, so we don't have something like
		// ../../../etc/passwd
		if (!uuidRe.matcher(feedId).matches())
			return null;
		
		// create the temporary file
		File tempFile;
		try {
			tempFile = File.createTempFile("gtfs", feedId);
		} catch (IOException e) {
			Logger.error("Could not create temp file to store feed %s", feedId);
			e.printStackTrace();
			return null;
		}
		
		// This is the best we can do for now
		tempFile.deleteOnExit();
		
		GetObjectRequest request = new GetObjectRequest(this.bucket, feedId);
		s3Client.getObject(request, tempFile);
		return tempFile;
	}
}
