/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2012 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import jgnash.engine.db4o.Db4oDataStore;
import jgnash.engine.xstream.XMLDataStore;

/**
 * Class to identify file type
 *
 * @author Craig Cavanaugh
 * @version $Id: FileMagic.java 3082 2012-01-08 02:26:38Z ccavanaugh $
 */
public class FileMagic {

    private static final Pattern COLON_DELIMITER_PATTERN = Pattern.compile(":");

    public static final String UTF_8 = "UTF-8";

    public static enum FileType {
        db4o,
        OfxV1,
        OfxV2,
        jGnash1XML,
        jGnash2XML,
        unknown
    }

    /**
     * Returns the file type
     *
     * @param file file to identify
     * @return identified file type
     */
    public static FileType magic(final File file) {

        if (isdb4o(file)) {
            return FileType.db4o;
        } else if (isValidjGnash1File(file)) {
            return FileType.jGnash1XML;
        } else if (isValidjGnash2File(file)) {
            return FileType.jGnash2XML;
        } else if (isOfxV1(file)) {
            return FileType.OfxV1;
        } else if (isOfxV2(file)) {
            return FileType.OfxV2;
        }

        return FileType.unknown;
    }

    /**
     * Determine the correct character encoding of an OFX Version 1 file
     *
     * @param file File to look at
     * @return encoding of the file
     */
    public static String getOfxV1Encoding(final File file) {
        String encoding = null;
        String charset = null;

        if (file.exists()) {
            Logger logger = Logger.getLogger(FileUtils.class.getName());

            BufferedReader reader = null;

            try {
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();

                while (line != null) {
                    line = line.trim();

                    if (line.length() > 0) { // allow empty lines at the beginning of the file
                        if (line.startsWith("ENCODING:")) {
                            String[] splits = COLON_DELIMITER_PATTERN.split(line);

                            if (splits.length == 2) {
                                encoding = splits[1];
                            }
                        } else if (line.startsWith("CHARSET:")) {
                            String[] splits = COLON_DELIMITER_PATTERN.split(line);

                            if (splits.length == 2) {
                                charset = splits[1];
                            }
                        }

                        if (encoding != null && charset != null) {
                            break;
                        }
                    }
                    line = reader.readLine();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.toString(), e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, e.toString(), e);
                    }
                }
            }
        }

        if (encoding != null && charset != null) {
            if (encoding.equals(UTF_8) && charset.equals("CSUNICODE")) {
                //return "UTF-8";
                return "ISO-8859-1";
            } else if (encoding.equals(UTF_8)) {
                return UTF_8;
            } else if (encoding.equals("USASCII") && charset.equals("1252")) {
                return "windows-1252";
            } else if (encoding.equals("USASCII") && charset.contains("8859-1")) {
                return "ISO-8859-1";
            } else if (encoding.equals("USASCII") && charset.equals("NONE")) {
                return "windows-1252";
            }
        }

        return "windows-1252";
    }

    public static boolean isOfxV1(final File file) {

        boolean result = false;

        if (file.exists()) {
            Logger logger = Logger.getLogger(FileUtils.class.getName());

            BufferedReader reader = null;

            try {
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();

                while (line != null) {
                    line = line.trim();

                    if (line.length() > 0) { // allow empty lines at the beginning of the file
                        if (line.startsWith("OFXHEADER:")) {
                            result = true;
                        }
                        break;
                    }
                    line = reader.readLine();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.toString(), e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, e.toString(), e);
                    }
                }
            }
        }

        return result;
    }

    public static boolean isOfxV2(final File file) {

        boolean result = false;

        if (file.exists()) {
            Logger logger = Logger.getLogger(FileUtils.class.getName());

            BufferedReader reader = null;

            try {
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();

                while (line != null) {

                    line = line.trim();

                    // consume any processing instructions and check for ofx 2.0 hints
                    if (line.length() > 0 && line.startsWith("<?")) {
                        if (line.startsWith("<?OFX") && line.contains("OFXHEADER=\"200\"")) {
                            result = true;
                            break;
                        }
                    } else if (line.length() > 0) { // allow empty lines at the beginning of the file
                        if (line.startsWith("<OFX>")) { //must be ofx
                            result = true;
                        }
                        break;
                    }

                    line = reader.readLine();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.toString(), e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, e.toString(), e);
                    }
                }
            }
        }

        return result;
    }

    static boolean isdb4o(File file) {
        boolean result = false;

        if (file.exists()) {
            Logger log = Logger.getLogger(FileUtils.class.getName());

            /* Search for db4o type first */
            RandomAccessFile di = null;

            try {
                byte[] header = new byte[4];

                di = new RandomAccessFile(file, "r");

                if (di.length() > 0) { // must not be a zero length file
                    di.readFully(header);

                    if (new String(header).equals("db4o")) {
                        result = true;
                    }
                }
                di.close();
            } catch (IOException ex) {
                log.log(Level.SEVERE, null, ex);
            } finally {
                if (di != null) {
                    try {
                        di.close();
                    } catch (IOException ex) {
                        log.log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        return result;
    }

    public static boolean isValidjGnash1File(final File file) {
        return isValidjGnashX(file, "1");
    }

    private static boolean isValidjGnash2File(final File file) {
        return isValidjGnashX(file, "2");
    }

    private static boolean isValidjGnashX(final File file, final String majorVersion) {
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return false;
        }

        return getjGnashXMLVersion(file).startsWith(majorVersion);
    }

    public static float getjGnashdb4oVersion(final File file) {
        return Db4oDataStore.getFileVersion(file);
    }
    
    public static float getXStreamXmlVersion(final File file) {
        return XMLDataStore.getFileVersion(file);
    }

    public static String getjGnashXMLVersion(final File file) {
        String version = "";

        Logger logger = Logger.getLogger(FileUtils.class.getName());

        XMLInputFactory inputFactory = XMLInputFactory.newInstance();

        InputStream input = null;
        XMLStreamReader reader = null;

        try {
            input = new BufferedInputStream(new FileInputStream(file));
            reader = inputFactory.createXMLStreamReader(input, UTF_8);

            parse:
            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.PROCESSING_INSTRUCTION:
                        String name = reader.getPITarget();
                        String data = reader.getPIData();

                        if (name.equals("fileVersion")) {
                            version = data;
                            break parse;
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (FileNotFoundException | IllegalStateException e) {
            logger.log(Level.SEVERE, e.toString(), e);
        } catch (XMLStreamException e) {
            logger.log(Level.INFO, "{0} was not a valid jGnash XML file", file.getAbsolutePath());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }

            if (input != null) {
                try {
                    input.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }

        return version;
    }

    private FileMagic() {
    }
}