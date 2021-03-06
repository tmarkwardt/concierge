/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *     Jochen Hiller
 *******************************************************************************/
package org.eclipse.concierge.test.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.osgi.framework.Constants;

/**
 * This class will support to create synthetic bundles. It will provide a fluent
 * interface.
 */
public class SyntheticBundleBuilder {

	private final Map<Attributes.Name, String> manifestHeaders;
	private final Map<String, File> files;
	private final Map<String, String> content;

	public static SyntheticBundleBuilder newBuilder() {
		return new SyntheticBundleBuilder();
	}

	public SyntheticBundleBuilder() {
		// preserve files and added content
		this.files = new HashMap<String, File>();
		this.content = new HashMap<String, String>();
		// pre-fill default headers
		final Map<Attributes.Name, String> headers = new HashMap<Attributes.Name, String>();
		headers.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		headers.put(new Attributes.Name(Constants.BUNDLE_MANIFESTVERSION), "2");
		headers.put(new Attributes.Name(Constants.BUNDLE_VERSION), "0.0.0");
		this.manifestHeaders = headers;
	}

	public SyntheticBundleBuilder bundleSymbolicName(final String bsn) {
		this.manifestHeaders.put(new Attributes.Name(
				Constants.BUNDLE_SYMBOLICNAME), bsn);
		return this;
	}

	public SyntheticBundleBuilder singleton() {
		String bsn = this.manifestHeaders.get(new Attributes.Name(
				Constants.BUNDLE_SYMBOLICNAME));
		bsn = bsn + ";singleton:=true";
		this.manifestHeaders.put(new Attributes.Name(
				Constants.BUNDLE_SYMBOLICNAME), bsn);
		return this;
	}

	public SyntheticBundleBuilder bundleVersion(final String version) {
		this.manifestHeaders.put(new Attributes.Name(Constants.BUNDLE_VERSION),
				version);
		return this;
	}

	public String getBundleSymbolicName() {
		return this.manifestHeaders.get(new Attributes.Name(
				Constants.BUNDLE_SYMBOLICNAME));
	}

	public String getBundleVersion() {
		final String version = this.manifestHeaders.get(new Attributes.Name(
				Constants.BUNDLE_VERSION));
		return version;
	}

	public SyntheticBundleBuilder addManifestHeader(final String key,
			final String value) {
		// add the header into Manifest
		this.manifestHeaders.put(new Attributes.Name(key), value);
		return this;
	}

	public SyntheticBundleBuilder addManifestHeaders(
			final Map<String, String> headers) {
		// add the headers into Manifest
		for (Iterator<Map.Entry<String, String>> iter = headers.entrySet()
				.iterator(); iter.hasNext();) {
			final Map.Entry<String, String> entry = iter.next();
			this.manifestHeaders.put(new Attributes.Name(entry.getKey()),
					entry.getValue());
		}
		return this;
	}

	/**
	 * Use addFile() for binary content.
	 */
	public SyntheticBundleBuilder addFile(final String resPath, final File file) {
		this.files.put(resPath, file);
		return this;
	}

	/**
	 * Use addFile() for simple text content.
	 */
	public SyntheticBundleBuilder addFile(final String resPath,
			final String content) {
		this.content.put(resPath, content);
		return this;
	}

	public InputStream asInputStream() {
		// copy MANIFEST to a jar file in memory
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final Manifest manifest = new Manifest();

		for (Iterator<Map.Entry<Attributes.Name, String>> iter = this.manifestHeaders
				.entrySet().iterator(); iter.hasNext();) {
			final Map.Entry<Attributes.Name, String> entry = iter.next();
			manifest.getMainAttributes().put(entry.getKey(), entry.getValue());
		}

		Set<String> writtenPaths = new HashSet<String>();
		
		JarOutputStream jarStream = null;
		try {
			jarStream = new JarOutputStream(out);
			
			writeFolders(jarStream, writtenPaths, "META-INF");
			ZipEntry e = new ZipEntry(JarFile.MANIFEST_NAME);
			jarStream.putNextEntry(e);
			manifest.write(jarStream);
			jarStream.closeEntry();

			// copy files into JAR
			for (Iterator<Map.Entry<String, File>> iter = this.files.entrySet()
					.iterator(); iter.hasNext();) {
				final Map.Entry<String, File> entry = iter.next();
				final String resPath = entry.getKey();
				if (resPath.contains("/")) {
					writeFolders(jarStream, writtenPaths, resPath.substring(0, resPath.lastIndexOf("/")));
				}
				final File f = entry.getValue();
				final FileInputStream fis = new FileInputStream(f);
				final JarEntry je = new JarEntry(resPath);
				jarStream.putNextEntry(je);
				TestUtils.copyStream(fis, jarStream);
				jarStream.closeEntry();
				fis.close();
			}

			// copy content into JAR
			for (Iterator<Map.Entry<String, String>> iter = this.content
					.entrySet().iterator(); iter.hasNext();) {
				final Map.Entry<String, String> entry = iter.next();
				final String resPath = entry.getKey();
				if (resPath.contains("/")) {
					writeFolders(jarStream, writtenPaths, resPath.substring(0, resPath.lastIndexOf("/")));
				}
				final String s = entry.getValue();
				final InputStream stringInputStream = new ByteArrayInputStream(
						s.getBytes(Charset.forName("UTF-8")));
				final JarEntry je = new JarEntry(resPath);
				jarStream.putNextEntry(je);
				TestUtils.copyStream(stringInputStream, jarStream);
				jarStream.closeEntry();
				stringInputStream.close();
			}

			jarStream.close();
			final InputStream is = new ByteArrayInputStream(out.toByteArray());
			return is;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			if (jarStream != null) {
				try {
					jarStream.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
	}

	private void writeFolders(JarOutputStream jarStream, Set<String> writtenPaths, 
			final String resPath) throws IOException {
		if (resPath.contains("/")) {
			writeFolders(jarStream, writtenPaths, resPath.substring(0, resPath.lastIndexOf("/")));
		}
		String folderName = resPath + "/";
		if (!writtenPaths.contains(folderName)) {
			writtenPaths.add(folderName);
			jarStream.putNextEntry(new ZipEntry(folderName));
			jarStream.closeEntry();
		}
	}

	public File asFile() {
		File destFile = new File("concierge-" + this.getBundleSymbolicName()
				+ "-" + getBundleVersion() + ".jar");
		destFile.deleteOnExit();
		String destFileName = destFile.getAbsolutePath();
		File createdDestFile = asFile(destFileName);
		return createdDestFile;
	}

	public File asFile(String destFileName) {
		final InputStream is = this.asInputStream();
		try {
			File destFile = new File(destFileName);
			// ensure parent dir will be created
			new File(destFile.getParent()).mkdirs();
			TestUtils.copyStreamToFile(is, destFile);
			return destFile;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void clearManifestHeaders() {
		manifestHeaders.clear();
	}
}
