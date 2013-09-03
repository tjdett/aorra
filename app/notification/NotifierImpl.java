package notification;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import models.Flag;
import models.Notification;
import models.NotificationDAO;
import models.User;
import models.UserDAO;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.id.NodeId;
import org.jcrom.Jcrom;

import play.Application;
import play.Logger;
import play.api.libs.iteratee.Concurrent;
import play.api.libs.iteratee.Input;
import play.libs.F;
import scala.Tuple2;
import scala.concurrent.duration.Duration;
import service.JcrSessionFactory;
import service.filestore.EventManager;
import service.filestore.EventManager.EventReceiver;
import service.filestore.EventManager.EventReceiverMessage;
import service.filestore.EventManager.Event;
import service.filestore.FileStore;
import service.filestore.FlagStore;
import service.filestore.OrderedEvent;
import akka.actor.TypedActor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

public class NotifierImpl implements Notifier, TypedActor.PreStart {

  public static final String WAIT_MILLIS_KEY = "notifications.waitMillis";

  private final long maxHoldMs;

  private String lastEventId;

  private final List<Pair<Long, Event>> events = Lists.newArrayList();

  private final JcrSessionFactory sessionFactory;
  private final FileStore fileStore;
  private final FlagStore flagStore;
  private final Jcrom jcrom;

  @Inject
  public NotifierImpl(Application application,
      JcrSessionFactory sessionFactory, FileStore fileStore,
      FlagStore flagStore, Jcrom jcrom) {
    this.sessionFactory = sessionFactory;
    this.fileStore = fileStore;
    this.flagStore = flagStore;
    this.jcrom = jcrom;
    this.maxHoldMs = application.configuration().getLong(WAIT_MILLIS_KEY,
        15000L);
    updateUserModels();
  }

  // Handle old users without notifications by resaving the model
  private void updateUserModels() {
    Logger.debug("Updating user models");
    sessionFactory.inSession(new F.Function<Session, Session>() {
      @Override
      public Session apply(Session session) {
        UserDAO dao = new UserDAO(session, jcrom);
        for (User u : dao.list())
          dao.update(u);
        return session;
      }
    });
  }

  @Override
  public void preStart() {
    scheduleTick();
  }

  protected void scheduleTick() {
    final Notifier n = TypedActor.<Notifier>self();
    final EventReceiver er = new EventReceiver() {
      @Override
      public void end() {}

      @Override
      public void end(Throwable e) {}

      @Override
      public void push(OrderedEvent e) {
        n.tick();
      }
    };
    fileStore.getEventManager().tell(EventReceiverMessage.add(er, null));
  }

  @Override
  public void tick() {
    final List<Event> events = getNewEvents();
    if (events.isEmpty()) {
      return;
    }
    sessionFactory.inSession(new F.Function<Session, Session>() {
      @Override
      public Session apply(Session session) throws RepositoryException {
        processEvents(session, events);
        return session;
      }
    });
  }

  private List<Event> getNewEvents() {
    List<Event> result = Lists.newArrayList();
    if (lastEventId == null) {
      lastEventId = fileStore.getEventManager().getLastEventId();
    }
    Iterable<OrderedEvent> events = fileStore.getEventManager()
        .getSince(lastEventId);
    for (OrderedEvent oe : events) {
      String eventId = oe.id();
      lastEventId = eventId;
      EventManager.Event event = oe.event();
      if (event.info != null
          && event.info.type == EventManager.Event.NodeType.NOTIFICATION) {
        continue;
      }
      result.add(event);
      try {
        Logger.debug(String.format(
            "got event id %s type %s event info type %s with node id %s",
            eventId, event.type, event.info.type, event.info.id));
      } catch (Exception e) {
      }
    }
    return result;
  }

  private void processEvents(Session session, List<Event> events)
      throws RepositoryException {
    for (Event event : events) {
      if ((event.info != null)
          && (event.info.type == EventManager.Event.NodeType.FLAG)
          && isEditFlag(session, event)) {
        Set<String> emails = getWatchEmails(session,
            getTargetId(session, event));
        sendEditNotification(session, emails, event);
      } else {
        sendNotification(session, Lists.newArrayList(event));
      }
    }
  }

  private Set<String> getWatchEmails(Session session, String nodeId) {
    Set<String> emails = Sets.newHashSet();
    FlagStore.Manager manager = flagStore.getManager(session);
    Set<Flag> flags = manager.getFlags(FlagStore.FlagType.WATCH);
    for (Flag flag : flags) {
      if (flag.getTargetId().equals(nodeId)) {
        emails.add(getEmailAddress(flag.getUser()));
      } else {
        try {
          if (((SessionImpl) session).getHierarchyManager().isAncestor(
              new NodeId(flag.getTargetId()), new NodeId(nodeId))) {
            emails.add(getEmailAddress(flag.getUser()));
          }
        } catch (Exception e) {
        }
      }
    }
    return emails;
  }

  private String getEmailAddress(User u) {
    return String.format("%s <%s>", u.getName(), u.getEmail());
  }

  private void sendNotification(Session session, final List<Event> events)
      throws RepositoryException {
    FileStore.Manager manager = fileStore.getManager(session);
    Map<String, List<Event>> notifications = Maps.newHashMap();
    for (Event event : events) {
      Set<String> emails = getWatchEmails(session, event.info.id);
      for (String email : emails) {
        List<Event> eList = notifications.get(email);
        if (eList == null) {
          eList = Lists.newArrayList();
          notifications.put(email, eList);
        }
        eList.add(event);
      }
    }
    for (final String email : notifications.keySet()) {
      final List<Event> e = notifications.get(email);
      Map<String, FileStore.FileOrFolder> items = getItems(manager, e);
      final String message = views.html.notification.notification.render(e,
          items).toString();
      sendNotification(session, email, message);
    }
  }

  private Map<String, FileStore.FileOrFolder> getItems(
      FileStore.Manager manager, List<Event> events) throws RepositoryException {
    final Map<String, FileStore.FileOrFolder> m = Maps.newHashMap();
    for (final Event event : events) {
      final String fofId = event.info.id;
      if (!m.containsKey(fofId))
        m.put(fofId, manager.getByIdentifier(fofId));
    }
    return m;
  }

  private String getTargetId(Session session, Event event) {
    try {
      Flag flag = flagStore.getManager(session).getFlag(event.info.id);
      return flag.getTargetId();
    } catch (Exception e) {
      return null;
    }
  }

  private boolean isEditFlag(Session session, Event event) {
    try {
      FlagStore.Manager mgr = flagStore.getManager(session);
      Flag flag = mgr.getFlag(event.info.id);
      for (Flag f : mgr.getFlags(FlagStore.FlagType.EDIT)) {
        if (f.getId().equals(flag.getId())) {
          return true;
        }
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private void sendEditNotification(Session session, final Set<String> emails,
      final Event event) throws RepositoryException {
    try {
      Flag flag = flagStore.getManager(session).getFlag(event.info.id);
      String fofId = flag.getTargetId();
      User user = flag.getUser();
      FileStore.Manager manager = fileStore.getManager(session);
      FileStore.FileOrFolder ff = manager.getByIdentifier(fofId);
      Event.NodeType type;
      if (ff instanceof FileStore.File) {
        type = Event.NodeType.FILE;
      } else {
        type = Event.NodeType.FOLDER;
      }
      String path = ff.getPath();
      String msg = views.html.notification.edit_notification.render(path, type,
          user, fofId).toString();
      for (String email : emails) {
        sendNotification(session, email, msg);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void sendNotification(Session session, String email, String msg)
      throws ItemNotFoundException, RepositoryException {
    UserDAO userDao = new UserDAO(session, jcrom);
    User to = userDao.findByEmail(extractEmail(email));
    NotificationDAO notificationDao = new NotificationDAO(session, jcrom);
    Notification notification = new Notification(to, msg);
    if (to != null) {
      notificationDao.create(notification);
      session.save();
      fileStore.getEventManager().tell(EventManager.Event.create(notification));
    }
  }

  private String extractEmail(String email) {
    String e = StringUtils.substringBetween(email, "<", ">");
    if (StringUtils.isNotBlank(e)) {
      return e;
    } else {
      return email;
    }
  }

}