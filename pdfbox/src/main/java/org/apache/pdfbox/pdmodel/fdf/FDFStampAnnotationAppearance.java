/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.fdf;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.util.Hex;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parse the XML for the /AP entry tree.
 *
 * @author Andrew Hung
 */
class FDFStampAnnotationAppearance implements COSObjectable
{

    private static final Log LOG = LogFactory.getLog(FDFStampAnnotationAppearance.class);

    private COSDictionary dictionary;

    /**
     * Default constructor
     */
    FDFStampAnnotationAppearance()
    {
        dictionary = new COSDictionary();
        // the N entry is required.
        dictionary.setItem(COSName.N, new COSStream());
    }

    /**
     * Constructor for reading.
     *
     * @param dictionary The annotations dictionary.
     */
    FDFStampAnnotationAppearance(COSDictionary dictionary)
    {
        this.dictionary = dictionary;
    }

    /**
     * This will create an Appearance dictionary from an appearance XML document.
     *
     * @param fdfXML The XML document that contains the appearance data.
     */
    FDFStampAnnotationAppearance(Element appearanceXML) throws IOException
    {
        this();
        LOG.debug("Build dictionary for Appearance based on the appearanceXML");

        NodeList nodeList = appearanceXML.getChildNodes();
        Node node = null;
        Element child = null;
        String parentAttrKey = appearanceXML.getAttribute("KEY");
        LOG.debug("Appearance Root - tag: " + appearanceXML.getTagName() + ", name: " + 
                appearanceXML.getNodeName() + ", key: " + parentAttrKey + ", children: " + 
                nodeList.getLength());

        // Currently only handles Appearance dictionary (AP key on the root)
        if ("AP".equals(appearanceXML.getAttribute("KEY")))
        {
            for (int i = 0; i < nodeList.getLength(); i++)
            {
                node = nodeList.item(i);
                if (node instanceof Element)
                {
                    child = (Element) node;
                    if ("STREAM".equalsIgnoreCase(child.getTagName()))
                    {
                        LOG.debug(parentAttrKey +
                                " => Process " + child.getAttribute("KEY") + 
                                " item in the dictionary after processing the " + 
                                child.getTagName());
                        dictionary.setItem(child.getAttribute("KEY"), parseStreamElement(child));
                        LOG.debug(parentAttrKey + " => Set " + child.getAttribute("KEY"));
                    }
                    else
                    {
                        LOG.warn(parentAttrKey + " => Not handling element: " + child.getTagName());
                    }
                }
            }
        }
        else
        {
            LOG.warn(parentAttrKey + " => Not handling element: " + appearanceXML.getTagName() + 
                                     " with key: " + appearanceXML.getAttribute("KEY"));
        }
    }

    @Override
    public COSDictionary getCOSObject()
    {
        return dictionary;
    }

    private COSStream parseStreamElement(Element streamEl) throws IOException
    {
        COSStream stream = new COSStream();

        NodeList nodeList = streamEl.getChildNodes();
        Node node;
        Element child;
        String childAttrKey;
        String childAttrVal;
        String parentAttrKey = streamEl.getAttribute("KEY");

        for (int i = 0; i < nodeList.getLength(); i++)
        {
            node = nodeList.item(i);
            if (node instanceof Element)
            {
                child = (Element) node;
                childAttrKey = child.getAttribute("KEY");
                childAttrVal = child.getAttribute("VAL");
                LOG.debug(parentAttrKey + " => reading child: " + child.getTagName() +
                           " with key: " + childAttrKey);
                if ("INT".equalsIgnoreCase(child.getTagName()))
                {
                    if (!"Length".equals(childAttrKey))
                    {
                        stream.setInt(COSName.getPDFName(childAttrKey), Integer.parseInt(childAttrVal));
                        LOG.debug(parentAttrKey + " => Set " + childAttrKey + ": " + childAttrVal);
                    }
                }
                else if ("NAME".equalsIgnoreCase(child.getTagName()))
                {
                    stream.setName(COSName.getPDFName(childAttrKey), childAttrVal);
                    LOG.debug(parentAttrKey + " => Set " + childAttrKey + ": " + childAttrVal);
                }
                else if ("BOOL".equalsIgnoreCase(child.getTagName()))
                {
                    stream.setBoolean(COSName.getPDFName(childAttrKey), Boolean.parseBoolean(childAttrVal));
                    LOG.debug(parentAttrKey + " => Set Interpolate: " + childAttrVal);
                }
                else if ("ARRAY".equalsIgnoreCase(child.getTagName()))
                {
                    stream.setItem(COSName.getPDFName(childAttrKey), parseArrayElement(child));
                    LOG.debug(parentAttrKey + " => Set " + childAttrKey);
                }
                else if ("DICT".equalsIgnoreCase(child.getTagName()))
                {
                    stream.setItem(COSName.getPDFName(childAttrKey), parseDictElement(child));
                    LOG.debug(parentAttrKey + " => Set " + childAttrKey);
                }
                else if ("STREAM".equalsIgnoreCase(child.getTagName()))
                {
                    stream.setItem(COSName.getPDFName(childAttrKey), parseStreamElement(child));
                    LOG.debug(parentAttrKey + " => Set " + childAttrKey);
                }
                else if ("DATA".equalsIgnoreCase(child.getTagName()))
                {
                    LOG.debug(parentAttrKey + " => Handling DATA with encoding: " +
                            child.getAttribute("ENCODING"));
                    if ("HEX".equals(child.getAttribute("ENCODING")))
                    {
                        OutputStream os = null;
                        try
                        {
                            os = stream.createRawOutputStream();
                            os.write(Hex.decodeHex(child.getTextContent()));
                            LOG.debug(parentAttrKey + " => Data was streamed");
                        }
                        finally
                        {
                            if (os != null)
                            {
                                os.close();
                            }
                        }
                    }
                    else
                    {
                        LOG.warn(parentAttrKey + " => Not handling element DATA encoding: " +
                                child.getAttribute("ENCODING"));
                    }
                }
                else
                {
                    LOG.warn(parentAttrKey + " => Not handling child element: " + child.getTagName());
                }
            }
        }

        return stream;
    }

    private COSArray parseArrayElement(Element arrayEl) throws IOException
    {
        LOG.debug("Parse " + arrayEl.getAttribute("KEY") + " Array");
        COSArray array = new COSArray();
        NodeList nodeList = arrayEl.getElementsByTagName("FIXED");
        Node node;
        Element el;
        String elAttrKey = arrayEl.getAttribute("KEY");

        if ("BBox".equals(elAttrKey))
        {
            if (nodeList.getLength() < 4)
            {
                throw new IOException("BBox does not have enough coordinates, only has: " +
                        nodeList.getLength());
            }
        }
        else if ("Matrix".equals(elAttrKey))
        {
            if (nodeList.getLength() < 6)
            {
                throw new IOException("Matrix does not have enough coordinates, only has: " + 
                        nodeList.getLength());
            }
        }

        LOG.debug("There are " + nodeList.getLength() + " FIXED elements");

        for (int i = 0; i < nodeList.getLength(); i++)
        {
            node = nodeList.item(i);
            if (node instanceof Element)
            {
                el = (Element) node;
                LOG.debug(elAttrKey + " value(" + i + "): " + el.getAttribute("VAL"));
                array.add(new COSFloat(el.getAttribute("VAL")));
            }
        }

        return array;
    }

    private COSDictionary parseDictElement(Element dictEl) throws IOException
    {
        LOG.debug("Parse " + dictEl.getAttribute("KEY") + " Dictionary");
        COSDictionary dict = new COSDictionary();

        NodeList nodeList = dictEl.getChildNodes();
        Node node;
        Element child;
        String childAttrKey;
        String childAttrVal;
        String parentAttrKey = dictEl.getAttribute("KEY");

        for (int i = 0; i < nodeList.getLength(); i++)
        {
            node = nodeList.item(i);
            if (node instanceof Element)
            {
                child = (Element) node;
                childAttrKey = child.getAttribute("KEY");
                childAttrVal = child.getAttribute("VAL");

                if ("DICT".equals(child.getTagName()))
                {
                    LOG.debug(parentAttrKey + " => Handling DICT element with key: " + childAttrKey);
                    dict.setItem(COSName.getPDFName(childAttrKey), parseDictElement(child));
                    LOG.debug(parentAttrKey + " => Set " + childAttrKey);
                }
                else if ("STREAM".equals(child.getTagName()))
                {
                    LOG.debug(parentAttrKey + " => Handling STREAM element with key: " + childAttrKey);
                    dict.setItem(COSName.getPDFName(childAttrKey), parseStreamElement(child));
                }
                else if ("NAME".equals(child.getTagName()))
                {
                    LOG.debug(parentAttrKey + " => Handling NAME element with key: " + childAttrKey);
                    dict.setName(COSName.getPDFName(childAttrKey), childAttrVal);
                    LOG.debug(parentAttrKey + " => Set " + childAttrKey + ": " + childAttrVal);
                }
                else
                {
                    LOG.warn(parentAttrKey + " => NOT handling child element: " + child.getTagName());
                }
            }
        }

        return dict;
    }
}
