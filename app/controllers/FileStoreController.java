package controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import models.UserDAO;

import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.jcrom.Jcrom;

import play.Logger;
import play.libs.Json;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import play.mvc.Http.MultipartFormData;
import service.JcrSessionFactory;
import service.filestore.FileStore;

import com.feth.play.module.pa.PlayAuthenticate;
import com.feth.play.module.pa.user.AuthUser;
import com.feth.play.module.pa.user.EmailIdentity;
import com.google.inject.Inject;

public final class FileStoreController extends Controller {

  private final FileStore fileStore;
  private final Jcrom jcrom;
  private final JcrSessionFactory sessionFactory;

  @Inject
  public FileStoreController(final JcrSessionFactory sessionFactory,
      final Jcrom jcrom,
      final FileStore fileStore) {
    this.fileStore = fileStore;
    this.jcrom = jcrom;
    this.sessionFactory = sessionFactory;
  }

  @Security.Authenticated(Secured.class)
  public Result download(final String encodedFilePath) {
    final String filePath;
    try {
      filePath = URLDecoder.decode(encodedFilePath, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // Should never happen
      throw new RuntimeException(e);
    }
    final AuthUser user = PlayAuthenticate.getUser(ctx());
    return inUserSession(user, new F.Function<Session, Result>() {
      @Override
      public final Result apply(Session session) throws RepositoryException {
        final FileStore.Manager fm = fileStore.getManager(session);
        FileStore.FileOrFolder fof = fm.getFileOrFolder("/"+filePath);
        if (fof instanceof FileStore.File) {
          FileStore.File file = (FileStore.File) fof;
          return ok(file.getData()).as(file.getMimeType());
        } else {
          return notFound();
        }
      }
    });
  }

  @Security.Authenticated(Secured.class)
  public Result postUpload(final String folderPath) {
    final AuthUser user = PlayAuthenticate.getUser(ctx());
    return inUserSession(user, new F.Function<Session, Result>() {
      @Override
      public final Result apply(Session session) throws RepositoryException {
        final FileStore.Manager fm = fileStore.getManager(session);
        final FileStore.Folder folder;
        try {
          folder = fm.getFolder("/"+folderPath);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        if (folder == null) {
          return badRequest("A valid folder must be specified.")
              .as("text/plain");
        }
        final MultipartFormData body = request().body().asMultipartFormData();
        if (body == null) {
          return badRequest("POST must contain multipart form data.")
              .as("text/plain");
        }
        // Assemble the JSON response
        final ObjectNode json = Json.newObject();
        {
          final ArrayNode aNode = json.putArray("files");
          for (MultipartFormData.FilePart filePart : body.getFiles()) {
            final ObjectNode jsonFileData = Json.newObject();
            try {
              FileStore.File f = updateFileContents(folder, filePart);
              session.save();
              final String fileName = filePart.getFilename();
              final File file = filePart.getFile();
              Logger.info(String.format(
                "file %s content type %s uploaded to %s by %s",
                fileName, filePart.getContentType(),
                f.getPath(),
                user.getId()));
              jsonFileData.put("name", fileName);
              jsonFileData.put("size", file.length());
            } catch (AccessDeniedException ade) {
              return forbidden(String.format(
                  "Insufficient permissions to upload files to %s",
                  folder.getPath()));
            }
            aNode.add(jsonFileData);
          }
        }
        // even though we return json set the content type to text/html
        // to prevent IE/Opera from opening a download dialog as described here:
        // https://github.com/blueimp/jQuery-File-Upload/wiki/Setup
        return ok(json).as("text/html");
      }
    });
  }

  private FileStore.File updateFileContents(FileStore.Folder folder,
      MultipartFormData.FilePart filePart) throws RepositoryException {
    try {
      FileStore.File f = folder.getFile(filePart.getFilename());
      if (f == null) {
        f = folder.createFile(filePart.getFilename(),
            filePart.getContentType(),
            new FileInputStream(filePart.getFile()));
      } else {
        f.update(filePart.getContentType(),
          new FileInputStream(filePart.getFile()));
      }
      return f;
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private <A extends Object> A inUserSession(final AuthUser authUser,
      final F.Function<Session, A> f) {
    String userId = sessionFactory.inSession(new F.Function<Session, String>() {
      @Override
      public String apply(Session session) {
        String email = authUser instanceof EmailIdentity ?
          ((EmailIdentity)authUser).getEmail() :
          authUser.getId();
        return (new UserDAO(session, jcrom))
          .findByEmail(email)
          .getJackrabbitUserId();
      }
    });
    return sessionFactory.inSession(userId, f);
  }
}