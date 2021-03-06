/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2005, 2006 IBM Corporation
 *
 * See the accompanying LICENSE file for applicable license.

 */
package org.dita.dost.platform;

import static java.util.Arrays.*;

import java.io.*;
import java.util.*;

import org.dita.dost.log.DITAOTLogger;
import org.dita.dost.util.XMLUtils.AttributesBuilder;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Generate outputfile with templates.
 * @author Zhang, Yuan Peng
 */
final class FileGenerator extends XMLFilterImpl {

    private static final String EXTENSION_ID_ATTR = "id";
    private static final String EXTENSION_ELEM = "extension";
    private static final String EXTENSION_ATTR = "extension";
    private static final String BEHAVIOR_ATTR = "behavior";

    public static final String PARAM_LOCALNAME = "localname";
    public static final String PARAM_TEMPLATE = "template";

    private static final String DITA_OT_NS = "http://dita-ot.sourceforge.net";
    private static final String TEMPLATE_PREFIX = "_template.";

    private DITAOTLogger logger;
    /** Plug-in features. */
    private final Map<String, List<String>> featureTable;
    private final Map<String, Features> pluginTable;
    /** Template file. */
    private File templateFile;

    /**
     * Default Constructor.
     */
    public FileGenerator() {
        this(null, null);
    }

    /**
     * Constructor init featureTable.
     * @param featureTbl featureTbl
     */
    public FileGenerator(final Hashtable<String, List<String>> featureTbl, final Map<String, Features> pluginTable) {
        featureTable = featureTbl;
        this.pluginTable = pluginTable;
        templateFile = null;
    }

    /**
     * Set logger.
     * @param logger logger instance
     */
    public void setLogger(final DITAOTLogger logger) {
        this.logger = logger;
    }

    /**
     * Generator the output file.
     * @param fileName filename
     */
    public void generate(final File fileName) {
        final File outputFile = removeTemplatePrefix(fileName);
        templateFile = fileName;

        try (final InputStream in = new BufferedInputStream(new FileInputStream(fileName));
             final OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            final Transformer transformer = TransformerFactory.newInstance().newTransformer();
            XMLReader reader = XMLReaderFactory.createXMLReader();
            this.setContentHandler(null);
            this.setParent(reader);
            reader = this;
            final Source source = new SAXSource(reader, new InputSource(in));
            source.setSystemId(fileName.toURI().toString());
            final Result result = new StreamResult(out);
            transformer.transform(source, result);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            logger.error("Failed to transform " + fileName + ": " + e.getMessage(), e);
        }
    }

    private File removeTemplatePrefix(final File templateFile) {
        final String f = templateFile.getAbsolutePath();
        final int i = f.lastIndexOf(TEMPLATE_PREFIX);
        if (i != -1) {
            return new File(f.substring(0, i) + f.substring(i + TEMPLATE_PREFIX.length() - 1));
        } else {
            throw new IllegalArgumentException("File " + templateFile + " does not contain template prefix");
        }
    }

    // XMLFilter methods

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (!(DITA_OT_NS.equals(uri) && EXTENSION_ELEM.equals(localName))) {
            getContentHandler().endElement(uri, localName, qName);
        }
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
        IAction action = null;
        try {
            if (DITA_OT_NS.equals(uri) && EXTENSION_ELEM.equals(localName)) {
                // Element extension: <dita:extension id="extension-point" behavior="classname"/>
                action = (IAction)Class.forName(attributes.getValue(BEHAVIOR_ATTR)).newInstance();
                action.setLogger(logger);
                action.addParam(PARAM_TEMPLATE, templateFile.getAbsolutePath());
                for (int i = 0; i <  attributes.getLength(); i++) {
                    action.addParam(attributes.getLocalName(i), attributes.getValue(i));
                }
                final String extension = attributes.getValue(EXTENSION_ID_ATTR);
                //action.addParam("extension", extension);
                if (featureTable.containsKey(extension)) {
                    action.setInput(featureTable.get(extension));
                }
                action.setFeatures(pluginTable);
                action.getResult(getContentHandler());
            } else {
                final AttributesBuilder atts = new AttributesBuilder();
                final int attLen = attributes.getLength();
                for (int i = 0; i < attLen; i++) {
                    if (DITA_OT_NS.equals(attributes.getURI(i))) {
                        // Attribute extension: <element dita:extension="localname classname ..." dita:localname="...">
                        if (!(EXTENSION_ATTR.equals(attributes.getLocalName(i)))) {
                            final String extensions = attributes.getValue(DITA_OT_NS, EXTENSION_ATTR);
                            final StringTokenizer extensionTokenizer = new StringTokenizer(extensions);
                            // Get the classname that implements this localname.
                            while (extensionTokenizer.hasMoreTokens()) {
                                final String thisExtension = extensionTokenizer.nextToken();
                                final String thisExtensionClass = extensionTokenizer.nextToken();
                                if (thisExtension.equals(attributes.getLocalName(i))) {
                                    action = (IAction)Class.forName(thisExtensionClass).newInstance();
                                    break;
                                }
                            }
                            action.setLogger(logger);
                            action.setFeatures(pluginTable);
                            action.addParam(PARAM_TEMPLATE, templateFile.getAbsolutePath());
                            action.setInput(asList(attributes.getValue(i).split(Integrator.FEAT_VALUE_SEPARATOR)));
                            atts.add(attributes.getLocalName(i), action.getResult());
                        }
                    } else if (attributes.getQName(i).startsWith("xmlns:") && DITA_OT_NS.equals(attributes.getValue(i))) {
                        // Ignore xmlns:dita.
                    } else {
                        // Normal attribute.
                        atts.add(attributes.getURI(i), attributes.getLocalName(i), attributes.getQName(i), attributes.getType(i), attributes.getValue(i));
                    }
                }
                getContentHandler().startElement(uri, localName, qName, atts.build());
            }
        } catch (final Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e) ;
        }
    }

}
