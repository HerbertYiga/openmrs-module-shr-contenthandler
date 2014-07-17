/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.shr.contenthandler.api;

import java.io.IOException;
import java.io.Serializable;
import java.util.Locale;

import org.openmrs.module.shr.contenthandler.DataUtil;

/**
 * A text based data payload.
 * <p>
 * This class follows the HL7 specification for ED datatypes (Encapsulated Data), but with the addition of both a type and format code field.
 * <p>
 * The format code is a globally unique code identifying the format of the content and is determined as part of the implementation
 * (e.g. IHE specifies various format codes for use with XDS documents).
 */
public final class Content implements Comparable<Content>, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 0L;


	public static enum Representation {
		/**
		 * Text
		 */
		TXT,
		/**
		 * Base64
		 */
		B64,
		/**
		 * Binary.
		 * <p>
		 * Only applicable if the payload is a URL and
		 * indicates that the data stored at the URL is binary.
		 */
		BINARY
	}
	
	public static enum CompressionFormat {
		/**
		 * Deflate (RFC 1951)
		 */
		DF,
		/**
		 * GZip (RFC 1952)
		 */
		GZ,
		/**
		 * ZLib (RFC 1950)
		 */
		ZL,
		//Since Compress (Z) is deprecated by HL7, we won't implement support for it
		//(Note that only the Deflate algorithm is actually required to be implemented)
		/**
		 * Compress
		 * <p>
		 * Not supported in {@link Content#getRawData()}
		 * 
		 * @deprecated
		 */
		Z
	}
	
	private final CodedValue typeCode;
	private final CodedValue formatCode;
	private final String contentType;
	private final String encoding;
	private final Representation representation;
	private final CompressionFormat compressionFormat;
	private final Locale language;
	private final boolean payloadIsUrl;
	private final String payload;
	
	
	/**
	 * Creates a new Content object with a simple text payload. Good if the payload is an XML document for example.
	 * 
	 * @see #Content(String, boolean, String, String, String, String, Representation, CompressionFormat, Locale)
	 */
	public Content(String payload, CodedValue typeCode, CodedValue formatCode, String contentType) {
		this(payload, false, typeCode, formatCode, contentType, null, Representation.TXT, null, null);
	}
	
	/**
	 * Creates a new Content object.
	 * 
	 * @param payload			The payload can either contain the content or a url referencing the content's location
	 * @param payloadIsUrl		The payload contains a URL string referencing a location where the content can be retrieved from.
	 * Note that the other metadata (e.g. content type) applies to the data, not the payload (i.e. the url string)
	 * @param typeCode			The content type code
	 * @param formatCode		The content format code
	 * @param contentType		The content MIME type
	 * @param encoding			(Nullable) The character set used for the content
	 * @param representation	Indicates that the content is either text or base64 encoded
	 * @param compressionFormat (Nullable) The compression algorithm used by the content
	 * @param language			(Nullable) The content language
	 */
	public Content(String payload, boolean payloadIsUrl, CodedValue typeCode, CodedValue formatCode, String contentType, String encoding, Representation representation, CompressionFormat compressionFormat, Locale language) {
		this.payload = payload;
		this.typeCode = typeCode;
		this.formatCode = formatCode;
		this.contentType = contentType;
		this.encoding = encoding;
		this.representation = representation;
		this.compressionFormat = compressionFormat;
		this.language = language;
		this.payloadIsUrl = payloadIsUrl;
		
		if (isCompressed() && !payloadIsUrl && !representation.equals(Representation.B64))
			throw new InvalidRepresentationException("Compressed payload must be Base64 encoded");
		
		if (!payloadIsUrl && representation.equals(Representation.BINARY))
			throw new InvalidRepresentationException("Binary payload must be Base64 encoded.");
	}
	
	
	public CodedValue getTypeCode() {
		return typeCode;
	}
	
	public CodedValue getFormatCode() {
		return formatCode;
	}
	
	public String getContentType() {
		return contentType;
	}
	
	/**
	 * Can be null
	 */
	public String getEncoding() {
		return encoding;
	}
	
	public Representation getRepresentation() {
		return representation;
	}
	
	public boolean isCompressed() {
		return compressionFormat!=null;
	}
	
	/**
	 * Can be null
	 */
	public CompressionFormat getCompressionFormat() {
		return compressionFormat;
	}
	
	/**
	 * Can be null
	 */
	public Locale getLanguage() {
		return language;
	}
	
	public boolean payloadIsUrl() {
		return payloadIsUrl;
	}
	
	
	public String getPayload() {
		return payload;
	}
	
	/**
	 * Returns the raw data represented by this Content object.
	 * 
	 * If the data is Base64 encoded it will be decoded, if it is compressed it will be decompressed
	 * and if the payload references a URL, the data will be fetched from the URL.
	 * 
	 * @should return the payload string as a byte array if it's not encoded or compressed
	 * @should retrieve the data from a url if the payload is a url
	 * @should decode the data if it is base64 encoded
	 * @should decompress the data if it is compressed
	 */
	public byte[] getRawData() throws IOException {
		byte[] data = null;
		
		if (payloadIsUrl)
			data = DataUtil.fetchPayloadFromURL(payload);
		else
			data = payload.getBytes();
		
		if (representation.equals(Representation.B64))
			data = DataUtil.decodeBase64(data);
		
		if (isCompressed()) {
			switch (compressionFormat) {
				case DF: data = DataUtil.uncompressDeflate(data); break;
				case GZ: data = DataUtil.uncompressGZip(data); break;
				case ZL: data = DataUtil.uncompressZLib(data); break;
				case Z: throw new UnsupportedOperationException("Decompression for Compress (Z) algorithm not supported");
			}
		}
		
		return data;
	}

	
	/**
	 * Two Content objects are considered equal if their payloads are equal.
	 */
	@Override
	public int compareTo(Content o) {
		return payload.compareTo(o.payload);
	}

	/**
	 * Two Content objects are considered equal if their payloads are equal.
	 */
	@Override
	public boolean equals(Object obj) {
		return obj!=null && (obj instanceof Content) && compareTo((Content)obj)==0;
	}
}
