package com.paytm.acquirer.netc.util;

import com.paytm.acquirer.netc.enums.ErrorMessage;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@UtilityClass
public class SFTPUtil {

  private static final Logger log = LoggerFactory.getLogger(SFTPUtil.class);

  public static File createZipFile(final List<File> files, final String filename) {

    final File zipFile = new File(  filename + ".zip");
    byte[] buf = new byte[1024 * 1024];
    try (final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {

      for (final File file : files) {
        boolean isSuccess = addFileToZipFile(filename, buf, out, file);
        if(!isSuccess) {
          return null;
        }
       }
      return zipFile;
    }
    catch (final IOException ex) {
      log.error(
        "Got io exception in creating zip file, files: {}, filename: {}, exception: ",
        ex,
        files,
        filename);
    } catch (Exception e) {
      log.error(
        "Got exception in creating zip file, files: {}, filename: {}, exception: ",
        e,
        files,
        filename);
    }
    return null;
  }

  private static boolean addFileToZipFile(String filename, byte[] buf, ZipOutputStream out, File file) {

    try (final FileInputStream in = new FileInputStream(file.getCanonicalPath())) {
      out.putNextEntry(new ZipEntry(file.getName()));
      int len;
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }
      out.closeEntry();
    }
    catch (Exception e) {
      log.error(
        "Got exception in creating zip file, file: {}, filename: {}, exception: ",
        e,
        file,
        filename);
      return false;
    }
    return true;
  }

  public static <T> T convertToObject(String element, Class<T> clazz, String delimiter) {
    String[] tokens = element.split(delimiter);
    Field[] field = clazz.getDeclaredFields();
    int i = 0;

    T refObject = null;
    try {
      refObject = clazz.newInstance();
    } catch (InstantiationException e) {
      log.error("Unable to crete new instance of the class", e);
      throw new NetcEngineException(ErrorMessage.INVALID_OBJECT_CONVERSION);
    } catch (IllegalAccessException e) {
      log.error("Illegal access of the data fields", e);
      throw new NetcEngineException(ErrorMessage.INVALID_OBJECT_CONVERSION);
    }
    for (String token : tokens) {
      try {
        assign(refObject, field[i], token);
        i++;
      } catch (IllegalAccessException e) {
        log.error("Illegal access of the data fields while setting value", e);
        throw new NetcEngineException(ErrorMessage.INVALID_OBJECT_CONVERSION);
      } catch(ArrayIndexOutOfBoundsException e) {
        log.error("array out of bound exception", e);
        throw new NetcEngineException(ErrorMessage.INVALID_OBJECT_CONVERSION);
      }
    }
    return refObject;
  }

  private static <T> Field assign(T o, Field f, String value)
    throws IllegalArgumentException, IllegalAccessException {
    f.setAccessible(true);
    if (f.getType() == Timestamp.class) {
      f.set(o, Timestamp.valueOf(LocalDateTime.parse(value,
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))));
    } else {
      f.set(o, value);
    }
    f.setAccessible(false);
    return f;
  }

  public static String getMessageId() {
    return getMessageId(LocalDate.now().atStartOfDay());
  }

  public static String getMessageId(LocalDateTime dateTime) {
    return Constants.MSG_PREFIX + Timestamp.valueOf(dateTime).getTime();
  }

  public static void cleanDirectory(File dir) throws IOException {
    FileUtils.deleteDirectory(dir);
  }

  public static void cleanFiles(List<String> fileNames) {
    for(String fileName : fileNames) {
      try {
        File file = new File(fileName);
        boolean isDeleted = Files.deleteIfExists(file.toPath());
        log.info("FIle {} is deleted {} ", fileName, isDeleted);
      }
      catch (IOException e) {
        log.error("Error while deleting file: {}", fileName, e);
      }
    }
  }

  public static void createLocalDirStructure(String dir) {
    createLocalDirStructure(dir, null);
  }

  public static void createLocalDirStructure(String parentDir, String nestedDir) {
    StringBuilder sb = new StringBuilder();
    sb.append(parentDir);
    if (!StringUtils.isEmpty(nestedDir)) {
      sb.append(File.separator).append(nestedDir);
    }
    File newDirectory = new File(sb.toString());
    if (!newDirectory.exists()) {
      if (newDirectory.mkdirs()) {
        log.info(
          "[SFTPUtil][createLocalDirStructure] parent directory created {} and nested dir {} ",
          parentDir, nestedDir);
      } else {
        log.warn(
          "[SFTPUtil][createLocalDirStructure] unable to parent directory created {} and nested dir {} ",
          parentDir, nestedDir);
      }
    } else {
      log.info("[SFTPUtil][createLocalDirStructure] already exists ");
    }
  }
}
