package controllers;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;
import static play.test.Helpers.callAction;
import static play.test.Helpers.charset;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.header;
import static play.test.Helpers.running;
import static play.test.Helpers.session;
import static play.test.Helpers.status;
import static test.AorraTestUtils.fakeAorraApp;
import static test.AorraTestUtils.asAdminUser;
import static test.AorraTestUtils.fileStore;
import static test.AorraTestUtils.injector;
import static test.AorraTestUtils.jcrom;
import static test.AorraTestUtils.sessionFactory;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Session;

import models.Flag;
import models.GroupManager;
import models.User;
import models.UserDAO;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.junit.Test;

import play.Play;
import play.test.FakeRequest;
import play.api.mvc.Call;
import play.libs.F;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import providers.JackrabbitEmailPasswordAuthProvider;
import service.filestore.FileStore;
import service.filestore.FlagStore;
import service.filestore.JsonBuilder;
import service.filestore.FlagStore.FlagType;
import service.filestore.roles.Admin;

public class FileStoreControllerTest {

  @Test
  public void routes() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        {
          final Call call =
              controllers.routes.FileStoreController.filestoreJson();
          assertThat(call.method()).isEqualTo("GET");
          assertThat(call.url()).isEqualTo("/filestore");
        }
        {
          final Call call =
              controllers.routes.FileStoreController.usersJson();
          assertThat(call.method()).isEqualTo("GET");
          assertThat(call.url()).isEqualTo("/user");
        }
        {
          final String id = UUID.randomUUID().toString();
          final Call call =
              controllers.routes.FileStoreController.showFile(id);
          assertThat(call.method()).isEqualTo("GET");
          assertThat(call.url()).isEqualTo("/file/"+id);
        }
        {
          final String id = UUID.randomUUID().toString();
          final Call call =
              controllers.routes.FileStoreController.showFolder(id);
          assertThat(call.method()).isEqualTo("GET");
          assertThat(call.url()).isEqualTo("/folder/"+id);
        }
        {
          final String id = UUID.randomUUID().toString();
          final Call call =
              controllers.routes.FileStoreController.mkdir(id, "foo");
          assertThat(call.method()).isEqualTo("POST");
          assertThat(call.url()).isEqualTo("/folder/"+id+"/folders?mkdir=foo");
        }
        {
          final String id = UUID.randomUUID().toString();
          final Call call =
              controllers.routes.FileStoreController.fileInfo(id);
          assertThat(call.method()).isEqualTo("GET");
          assertThat(call.url()).isEqualTo("/file/"+id+"/info");
        }
        {
          final String id = UUID.randomUUID().toString();
          final Call call =
              controllers.routes.FileStoreController.downloadFolder(id);
          assertThat(call.method()).isEqualTo("GET");
          assertThat(call.url()).isEqualTo("/folder/"+id+"/archive");
        }
        return session;
      }
    });
  }

  @Test
  public void getFilestoreJSON() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final Result result = callAction(
            controllers.routes.ref.FileStoreController.filestoreJson(),
            newRequest);
        assertThat(status(result)).isEqualTo(200);
        assertThat(contentType(result)).isEqualTo("application/json");
        assertThat(charset(result)).isEqualTo("utf-8");
        assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        final String expectedContent = (new JsonBuilder())
            .toJson(fileStore().getManager(session).getFolders())
            .toString();
        assertThat(contentAsString(result)).contains(expectedContent);
        return session;
      }
    });
  }

  @Test
  public void getFlagsJSON() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final FlagStore.Manager flm =
            injector().getInstance(FlagStore.class).getManager(session);
        final Flag flag =
            flm.setFlag(FlagType.WATCH, fm.getRoot().getIdentifier(), user);
        final Result result = callAction(
            controllers.routes.ref.FileStoreController.flagsJson(
                "watch"),
            newRequest);
        assertThat(status(result)).isEqualTo(200);
        assertThat(contentType(result)).isEqualTo("application/json");
        assertThat(charset(result)).isEqualTo("utf-8");
        assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        final String expectedContent = (new JsonBuilder())
            .toJson(flag)
            .toString();
        assertThat(contentAsString(result)).isEqualTo("["+expectedContent+"]");
        return session;
      }
    });
  }

  @Test
  public void getFlagJSON() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final FlagStore.Manager flm =
            injector().getInstance(FlagStore.class).getManager(session);
        final Flag flag =
            flm.setFlag(FlagType.WATCH, fm.getRoot().getIdentifier(), user);
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.flagJson(
                  FlagStore.FlagType.WATCH.toString(),
                  flag.getId()),
              newRequest);
          assertThat(status(result)).isEqualTo(200);
          assertThat(contentType(result)).isEqualTo("application/json");
          assertThat(charset(result)).isEqualTo("utf-8");
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
          final String expectedContent = (new JsonBuilder())
              .toJson(flag)
              .toString();
          assertThat(contentAsString(result)).isEqualTo(expectedContent);
        }
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.flagJson(
                  FlagStore.FlagType.EDIT.toString(),
                  flag.getId()),
              newRequest);
          assertThat(status(result)).isEqualTo(404);
        }
        return session;
      }
    });
  }

  @Test
  public void addFlag() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final FlagStore.Manager flm =
            injector().getInstance(FlagStore.class).getManager(session);
        final ObjectNode data = Json.newObject();
        data.put("targetId", fm.getRoot().getIdentifier());
        data.put("userId", user.getId());
        final Result result = callAction(
            controllers.routes.ref.FileStoreController.addFlag(
                FlagStore.FlagType.WATCH.toString()),
            newRequest.withJsonBody(data));
        assertThat(status(result)).isEqualTo(201);
        assertThat(contentType(result)).isEqualTo("application/json");
        assertThat(charset(result)).isEqualTo("utf-8");
        assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        final Flag flag =
            flm.getFlag(FlagType.WATCH, fm.getRoot().getIdentifier(), user);
        final String expectedContent = (new JsonBuilder())
            .toJson(flag)
            .toString();
        assertThat(contentAsString(result)).isEqualTo(expectedContent);
        return session;
      }
    });
  }

  @Test
  public void deleteFlag() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final FlagStore.Manager flm =
            injector().getInstance(FlagStore.class).getManager(session);
        final Flag flag =
            flm.setFlag(FlagType.WATCH, fm.getRoot().getIdentifier(), user);
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.deleteFlag(
                  FlagStore.FlagType.WATCH.toString(),
                  flag.getId()),
              newRequest);
          assertThat(status(result)).isEqualTo(204);
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
          assertThat(flm.hasFlag(FlagType.WATCH,
              fm.getRoot().getIdentifier(), user)).isFalse();
        }
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.deleteFlag(
                  FlagStore.FlagType.WATCH.toString(),
                  flag.getId()),
              newRequest);
          assertThat(status(result)).isEqualTo(404);
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        }
        return session;
      }
    });
  }

  @Test
  public void getFolderHTML() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.showFolder(
                  fm.getRoot().getIdentifier()),
              newRequest.withHeader("Accept", "text/html"));
          assertThat(status(result)).isEqualTo(200);
          assertThat(contentType(result)).isEqualTo("text/html");
          assertThat(charset(result)).isEqualTo("utf-8");
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        }
        return session;
      }
    });
  }

  @Test
  public void getFileHTML() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final FileStore.File file =
            fm.getRoot().createFile("test.txt", "text/plain",
                new ByteArrayInputStream("Some content.".getBytes()));
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.showFile(
                  file.getIdentifier()),
              newRequest.withHeader("Accept", "text/html"));
          assertThat(status(result)).isEqualTo(200);
          assertThat(contentType(result)).isEqualTo("text/html");
          assertThat(charset(result)).isEqualTo("utf-8");
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        }
        return session;
      }
    });
  }

  @Test
  public void getFolderJSON() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.showFolder(
                  fm.getRoot().getIdentifier()),
              newRequest.withHeader("Accept", "application/json"));
          assertThat(status(result)).isEqualTo(200);
          assertThat(contentType(result)).isEqualTo("application/json");
          assertThat(charset(result)).isEqualTo("utf-8");
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
          final String expectedContent = (new JsonBuilder())
              .toJsonShallow(fm.getRoot(), false)
              .toString();
          assertThat(contentAsString(result)).contains(expectedContent);
        }
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.showFolder(
                  (new UUID(0, 0)).toString()),
              newRequest.withHeader("Accept", "application/json"));
          assertThat(status(result)).isEqualTo(404);
        }
        return session;
      }
    });
  }

  @Test
  public void getFileJSON() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        // Non-existent ID should yield 404
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.showFile(
                  UUID.randomUUID().toString()),
              newRequest.withHeader("Accept", "application/json"));
          assertThat(status(result)).isEqualTo(404);
        }
        // Should be a Bad Request if called on a folder
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.showFile(
                  fm.getRoot().getIdentifier()),
              newRequest.withHeader("Accept", "application/json"));
          assertThat(status(result)).isEqualTo(400);
        }
        {
          final FileStore.File file =
              fm.getRoot().createFile("test.txt", "text/plain",
                  new ByteArrayInputStream("Some content.".getBytes()));
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.showFile(
                  file.getIdentifier()),
              newRequest.withHeader("Accept", "application/json"));
          assertThat(status(result)).isEqualTo(200);
          assertThat(contentType(result)).isEqualTo("application/json");
          assertThat(charset(result)).isEqualTo("utf-8");
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
          final String expectedContent = (new JsonBuilder())
              .toJsonShallow(file)
              .toString();
          assertThat(contentAsString(result)).contains(expectedContent);
        }
        return session;
      }
    });
  }

  @Test
  public void getFileInfoJSON() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.fileInfo(
                  fm.getRoot().getIdentifier()),
              newRequest.withHeader("Accept", "application/json"));
          assertThat(status(result)).isEqualTo(400);
        }
        {
          final FileStore.File file =
              fm.getRoot().createFile("test.txt", "text/plain",
                  new ByteArrayInputStream("Some content.".getBytes()));
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.fileInfo(
                  file.getIdentifier()),
              newRequest.withHeader("Accept", "application/json"));
          assertThat(status(result)).isEqualTo(200);
          assertThat(contentType(result)).isEqualTo("application/json");
          assertThat(charset(result)).isEqualTo("utf-8");
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
          final JsonNode content = Json.parse(contentAsString(result));
          assertThat(content.has("versions")).isTrue();
          for (JsonNode version : (ArrayNode) content.get("versions")) {
            assertThat(version.has("name")).isTrue();
            assertThat(version.get("name").asText()).isEqualTo("1.0");
            assertThat(version.has("author")).isFalse();
            assertThat(version.has("timestamp")).isTrue();
          }
        }
        return session;
      }
    });
  }

  @Test
  public void uploadToFolder() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        {
          // Updating with correct body
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.uploadToFolder(
                  fm.getRoot().getIdentifier()),
              newRequest.withHeader("Accept", "application/json")
                .withBody(
                    test.AorraScalaHelper.testMultipartFormBody(
                        "Some content.")));
          assertThat(contentAsString(result))
            .isEqualTo("{\"files\":[{\"name\":\"test.txt\",\"size\":13}]}");
          assertThat(status(result)).isEqualTo(200);
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
          assertThat(IOUtils.toString(
              ((FileStore.File)
                  fm.getRoot().getFileOrFolder("test.txt")).getData()))
              .isEqualTo("Some content.");
        }
        return session;
      }
    });
  }

  @Test
  public void updateFile() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final FileStore.File file =
            fm.getRoot().createFile("test.txt", "text/plain",
                new ByteArrayInputStream("Some content.".getBytes()));
        {
          // Try without body
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.updateFile(
                  file.getIdentifier()),
              newRequest.withHeader("Accept", "application/json"));
          // Should return 400 Bad Request
          assertThat(status(result)).isEqualTo(400);
        }
        {
          // Updating with correct body
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.updateFile(
                  file.getIdentifier()),
              newRequest.withHeader("Accept", "application/json")
                .withBody(
                    test.AorraScalaHelper.testMultipartFormBody(
                        "New content.")));
          assertThat(contentAsString(result))
            .isEqualTo("{\"files\":[{\"name\":\"test.txt\",\"size\":12}]}");
          assertThat(status(result)).isEqualTo(200);
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
          assertThat(IOUtils.toString(
              ((FileStore.File)
                  fm.getByIdentifier(file.getIdentifier())).getData()))
              .isEqualTo("New content.");
        }
        return session;
      }
    });
  }

  @Test
  public void deleteFile() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final FileStore.File file =
            fm.getRoot().createFile("test.txt", "text/plain",
                new ByteArrayInputStream("Some content.".getBytes()));
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.delete(
                  file.getIdentifier()),
              newRequest.withHeader("Accept", "application/json"));
          assertThat(status(result)).isEqualTo(204);
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        }
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.delete(
                  file.getIdentifier()),
              newRequest.withHeader("Accept", "application/json"));
          assertThat(status(result)).isEqualTo(404);
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        }
        return session;
      }
    });
  }

  @Test
  public void downloadFile() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final FileStore.File file =
            fm.getRoot().createFile("test.txt", "text/plain",
                new ByteArrayInputStream("Some content.".getBytes()));
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.downloadFile(
                  file.getIdentifier(),
                  "1.0"),
              newRequest.withHeader("Accept", "text/plain"));
          assertThat(status(result)).isEqualTo(200);
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
          assertThat(header("Content-Disposition", result))
            .startsWith("attachment; filename=");
          // File result is async
          assertThat(result.getWrappedResult()).isInstanceOf(
              play.api.mvc.AsyncResult.class);
        }
        return session;
      }
    });
  }

  @Test
  public void fileTextSummary() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final FileStore.File file =
            fm.getRoot().createFile("test.txt", "text/plain",
                new ByteArrayInputStream("Some content.".getBytes()));
        {
          final Result result = callAction(
              controllers.routes.ref.FileStoreController.fileTextSummary(
                  file.getIdentifier(),
                  "1.0"),
              newRequest.withHeader("Accept", "text/plain"));
          assertThat(status(result)).isEqualTo(200);
          assertThat(charset(result)).isEqualTo("utf-8");
          assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
          assertThat(contentAsString(result)).contains("Some content.");
        }
        return session;
      }
    });
  }

  @Test
  public void getGroupJson() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final FileStore.Manager fm = fileStore().getManager(session);
        final Result result = callAction(
            controllers.routes.ref.FileStoreController.groupPermissionList(
                fm.getRoot().getIdentifier()),
            newRequest);
        assertThat(status(result)).isEqualTo(200);
        assertThat(contentType(result)).isEqualTo("application/json");
        assertThat(charset(result)).isEqualTo("utf-8");
        assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        assertThat(contentAsString(result)).contains("testgroup");
        return session;
      }
    });
  }

  @Test
  public void getUsersJson() {
    asAdminUser(new F.Function3<Session, User, FakeRequest, Session>() {
      @Override
      public Session apply(
          final Session session,
          final User user,
          final FakeRequest newRequest) throws Throwable {
        final Result result = callAction(
            controllers.routes.ref.FileStoreController.usersJson(),
            newRequest);
        assertThat(status(result)).isEqualTo(200);
        assertThat(contentType(result)).isEqualTo("application/json");
        assertThat(charset(result)).isEqualTo("utf-8");
        assertThat(header("Cache-Control", result)).isEqualTo("no-cache");
        final ArrayNode json = JsonNodeFactory.instance.arrayNode();
        json.add((new JsonBuilder()).toJson(user));
        final String expectedContent = json.asText();
        assertThat(contentAsString(result)).contains(expectedContent);
        return session;
      }
    });
  }

}