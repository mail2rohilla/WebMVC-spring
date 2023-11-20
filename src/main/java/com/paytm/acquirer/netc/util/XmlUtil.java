package com.paytm.acquirer.netc.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class XmlUtil {
    private static ObjectMapper xmlMapper = new XmlMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static Document createXmlDocument(String xml) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        try {

            // * completely disable DOCTYPE declaration:
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
            return documentBuilder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new NetcEngineException("Error while parsing XML document from given string.", ex);
        }
    }

    public static String serializeXmlDocument(Document xmlDoc) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = factory.newTransformer();

            StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(stringWriter));

            return stringWriter.toString();
        } catch (TransformerConfigurationException tce) {
            throw new NetcEngineException("Can not create XML transformer : {}", tce);
        } catch (TransformerException te) {
            throw new NetcEngineException("Error while transforming given XML Doc to char stream : {}", te);
        }
    }

    public static <T> String serializeXmlDocument(T doc) {
        try {
            return xmlMapper.writeValueAsString(doc);
        } catch (JsonProcessingException jpe) {
            throw new NetcEngineException("Error while serializing xml object", jpe);
        }
    }

    public static <T> T deserializeXmlDocument(String serializedXml, Class<T> type) {
        try {
            return xmlMapper.readValue(serializedXml, type);
        } catch (IOException iox) {
            throw new NetcEngineException(String.format("Error while deserializing given xml string to type : %s", type.getName()), iox);
        }
    }

    public static String streamToString(InputStream stream) {

        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            for (int length; (length = stream.read(buffer)) != -1; ) {
                result.write(buffer, 0, length);
            }
            return result.toString();
        }
        catch (IOException iox) {
            throw new NetcEngineException("Unable to read input body ", iox);
        }
    }
}
