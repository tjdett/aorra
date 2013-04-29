var tree;
$(function () {
  $('#fileupload').fileupload({
    dataType: 'json',
    add: function (e, data) {
      $('#fileupload').fileupload(
        'option',
        'url',
        '/upload'+$('#folderselect').val()
      );
      data.submit();
    },
    done: function (e, data) {
      // Don't really need to do anything right now.
    }
  });
  tree = glyphtree($('#filetree'), {
    startExpanded: true,
    types: {
      folder: {
        icon: {
          "default": {
            content: "\uf07b",
            'font-family': "FontAwesome"
          },
          expanded: {
            content: "\uf07c",
            'font-family': "FontAwesome"
          }
        }
      },
      file: {
        icon: {
          leaf: {
            content: "\uf016",
            'font-family': "FontAwesome"
          }
        }
      }
    }
  });
  selectHandler = function(event, node) {
    if (node.type == 'folder') {
      $('.label.label-success').removeClass('label label-success');
      $("#folderselect").val(node.attributes.path);
      $(node.element()).children('.glyphtree-node-label')
        .addClass('label label-success');
    } else {
      window.open('/download'+node.attributes.path);
    }
  }
  tree.events.label.click = [selectHandler];

  function catchErrors(f) {
    return function(struct) { try { f.apply(this, arguments); } catch (e) {} };
  }

  var eventHandlers = {
    ping:   function(message) { /*console.log(message);*/ },
    load:   function(struct) { tree.load(struct); },
    create: catchErrors(function(struct) { tree.add(struct, struct.parentId) }),
    update: catchErrors(function(struct) { tree.update(struct) }),
    'delete': catchErrors(function(struct) { tree.remove(struct.id) })
  };

  var notificationUrl = '/filestore/notifications';

  // Are we using a modern browser, or are we using IE?
  if (typeof(window.EventSource) == 'undefined') {
    // HTML iframe
    function connect_htmlfile(url, callback) {
      var ifrDiv = document.createElement("div");
      var iframe = document.createElement("iframe");
      ifrDiv.setAttribute("style", "display: none");
      document.body.appendChild(ifrDiv);
      ifrDiv.appendChild(iframe);
      iframe.src = url;
      iframe.onload = function() { iframe.currentWindow.location.reload(); };
      // From: http://davidwalsh.name/window-iframe
      function eventHandler(w, eventName, handler) {
        var eMethod = w.addEventListener ? "addEventListener" : "attachEvent";
        var eName = w.addEventListener ? eventName : "on" + eventName;
        return w[eMethod](eName, handler, false);
      }
      // Receive messages from iframe
      eventHandler(window, 'message', function(e) {
        var msg = JSON.parse(e.data);
        callback(msg.type, msg.data);
      });
    }
    connect_htmlfile(notificationUrl, function(eventType, data) {
      eventHandlers[eventType](data);
    });
  } else {
    // EventSource
    var es = new EventSource(notificationUrl);
    es.addEventListener('ping', function(event) {
      eventHandlers.ping(event.data);
    });
    $.each(['load', 'create', 'update', 'delete'], function(i, n) {
      es.addEventListener(n, function(event) {
        var struct = JSON.parse(event.data);
        eventHandlers[n](struct);
      });
    });
  }
});