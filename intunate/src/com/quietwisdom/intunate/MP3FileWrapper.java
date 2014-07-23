package com.quietwisdom.intunate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.MutableInteger;


public class MP3FileWrapper {
	private File file = null;
	private String contentHash = null;
	private String fileHash = null;
	
	private String version;
	private int bitrate = 0;
	long lengthInSeconds = 0;
	private int xingOffset = -1;

	private int startOffset = -1;
	private int endOffset = -1;
	private int frameCount = 0;
	private Map<Integer, MutableInteger> bitrates = new HashMap<Integer, MutableInteger>();
	private int xingBitrate;
	private String channelMode;
	private String emphasis;
	private String modeExtension;
	private int sampleRate;
	private boolean original;
	private boolean vbr;
	private ID3v1 id3v1Tag;
	private ID3v2 id3v2Tag;
	private byte[] customTag;

	
	public MP3FileWrapper(File file, Mp3File mp3) throws Exception {
		this(file, mp3, MessageDigest.getInstance("SHA-256"));
	}

	public MP3FileWrapper(File file, Mp3File mp3, MessageDigest md) throws Exception {
		this.file = file;
		
		this.version = mp3.getVersion();
		this.bitrate = mp3.getBitrate();
		this.lengthInSeconds = mp3.getLengthInSeconds();
		this.xingOffset= mp3.getXingOffset();
		this.startOffset = mp3.getStartOffset();
		this.endOffset = mp3.getEndOffset();
		this.frameCount = mp3.getFrameCount();
		this.bitrates = mp3.getBitrates();
		this.xingBitrate = mp3.getXingBitrate();
		this.channelMode = mp3.getChannelMode();
		this.emphasis = mp3.getEmphasis();
		this.modeExtension = mp3.getModeExtension();
		this.sampleRate = mp3.getSampleRate();
		this.original = mp3.isOriginal();
		this.vbr = mp3.isVbr();
		this.id3v1Tag = mp3.getId3v1Tag();
		this.id3v2Tag = mp3.getId3v2Tag();
		this.customTag = mp3.getCustomTag();
		
		int start = mp3.getStartOffset();
		int end = mp3.getEndOffset();
		int bufferLength = (end-start);

		RandomAccessFile raf = null;
		try {
			raf =  new RandomAccessFile(file, "r");
			raf.seek(start);
			byte[] bytesBuffer = new byte[bufferLength];
			int bytesRead = raf.read(bytesBuffer, 0, bufferLength);
			if(bytesRead != bufferLength) {
				throw new Exception("Unable to obtain content buffer");
			}
			
			md.update(bytesBuffer);
			byte[] digestBytes = md.digest();
			this.contentHash = new String(digestBytes);
		} finally {
			if(null != raf) {
				try {
					raf.close();
				} catch(IOException ignored) {
				}
			}
		}
		
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			byte[] bytesBuffer = new byte[bufferLength];
			int bytesRead = 0; 
	        while ((bytesRead = fis.read(bytesBuffer)) != -1) {
	        	md.update(bytesBuffer, 0, bytesRead);
	        };
	        byte[] digestBytes = md.digest();
	        this.fileHash = new String(digestBytes);
		} finally {
			if(null != fis) {
				try {
					fis.close();
				} catch(IOException ignored) {
				}
			}
		}
	}
	
	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public String getContentHash() {
		return contentHash;
	}

	public String getHexContentHash() {
		return MP3FileWrapper.toHex(contentHash);
	}

	public void setContentHash(String contentHash) {
		this.contentHash = contentHash;
	}

	public String getFileHash() {
		return fileHash;
	}

	public String getHexFileHash() {
		return MP3FileWrapper.toHex(fileHash);
	}

	public void setFileHash(String fileHash) {
		this.fileHash = fileHash;
	}

	public String getVersion() {
		return version;
	}

	public int getBitrate() {
		return bitrate;
	}

	public long getLengthInSeconds() {
		return lengthInSeconds;
	}

	public int getXingOffset() {
		return xingOffset;
	}

	public int getStartOffset() {
		return startOffset;
	}

	public int getEndOffset() {
		return endOffset;
	}
	
	public int getBitLength() {
		return getEndOffset() - getStartOffset();
	}

	public int getFrameCount() {
		return frameCount;
	}

	public Map<Integer, MutableInteger> getBitrates() {
		return bitrates;
	}

	public int getXingBitrate() {
		return xingBitrate;
	}

	public String getChannelMode() {
		return channelMode;
	}

	public String getEmphasis() {
		return emphasis;
	}

	public String getModeExtension() {
		return modeExtension;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public boolean isOriginal() {
		return original;
	}

	public boolean isVbr() {
		return vbr;
	}

	public ID3v1 getId3v1Tag() {
		return id3v1Tag;
	}

	public ID3v2 getId3v2Tag() {
		return id3v2Tag;
	}

	public byte[] getCustomTag() {
		return customTag;
	}

	public static final String toHex(String raw) {
		byte[] rawBytes = raw.getBytes();

		StringBuffer buffy = new StringBuffer(rawBytes.length);
		for (int i = 0; i < rawBytes.length; i++) {
			buffy.append(Integer.toString((rawBytes[i] & 0xff) + 0x100, 16)
					.substring(1));
		}
		return buffy.toString();
		
		 
//	       //convert the byte to hex format method 2
//	        StringBuffer hexString = new StringBuffer();
//	    	for (int i=0;i<mdbytes.length;i++) {
//	    	  hexString.append(Integer.toHexString(0xFF & mdbytes[i]));
//	    	}
//	 
//	    	System.out.println("Hex format : " + hexString.toString());
	}

	
}
