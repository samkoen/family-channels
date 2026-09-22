/* Keep the child on Family Channels: block the YouTube logo, and play
   "more videos" picks in our player instead of youtube.com. */
(function (global) {
  var SANDBOX = "allow-scripts allow-same-origin allow-presentation allow-forms";

  function lockIframe(iframe) {
    if (!iframe || iframe.nodeName !== "IFRAME") return;
    if (iframe.getAttribute("sandbox") === SANDBOX) return;
    iframe.setAttribute("sandbox", SANDBOX);
    iframe.setAttribute(
      "allow",
      "autoplay; encrypted-media; picture-in-picture; fullscreen",
    );
  }

  function watchIframes() {
    document.querySelectorAll("iframe").forEach(lockIframe);
    new MutationObserver(function (records) {
      records.forEach(function (record) {
        record.addedNodes.forEach(function (node) {
          if (node.nodeName === "IFRAME") lockIframe(node);
          if (node.querySelectorAll) {
            node.querySelectorAll("iframe").forEach(lockIframe);
          }
        });
      });
    }).observe(document.documentElement, { childList: true, subtree: true });
  }

  function isYouTubeUrl(url) {
    var raw = String(url || "");
    if (!raw) return false;
    try {
      var host = new URL(raw, location.href).hostname.replace(/^www\./, "").toLowerCase();
      return (
        host === "youtu.be" ||
        host.endsWith(".youtu.be") ||
        host === "youtube.com" ||
        host.endsWith(".youtube.com") ||
        host === "youtube-nocookie.com" ||
        host.endsWith(".youtube-nocookie.com")
      );
    } catch (err) {
      return /youtube\.com|youtu\.be|youtube-nocookie\.com|vnd\.youtube|intent:/i.test(raw);
    }
  }

  function youtubeLeaveId(url) {
    try {
      var raw = String(url || "");
      if (!raw || !isYouTubeUrl(raw)) return "";
      if (/\/embed\//i.test(raw) && !/\/watch/i.test(raw)) return "";
      var match =
        raw.match(/[?&]v=([\w-]{6,20})/) ||
        raw.match(/youtu\.be\/([\w-]{6,20})/) ||
        raw.match(/\/(?:shorts|live)\/([\w-]{6,20})/) ||
        raw.match(/^vnd\.youtube:([\w-]{6,20})/i);
      return match ? match[1] : "";
    } catch (err) {
      return "";
    }
  }

  function trapExit(onRelated, getVideoId) {
    if (global.navigation && navigation.addEventListener) {
      navigation.addEventListener("navigate", function (event) {
        var dest = event.destination && event.destination.url;
        if (!isYouTubeUrl(dest)) return;
        if (event.cancelable) event.preventDefault();
        var id = youtubeLeaveId(dest);
        if (id && id !== getVideoId() && onRelated) onRelated(id);
      });
    }
    global.open = function (url) {
      if (isYouTubeUrl(url)) {
        var id = youtubeLeaveId(url);
        if (id && id !== getVideoId() && onRelated) onRelated(id);
      }
      return null;
    };
  }

  function addLogoShield(box) {
    if (!box || document.getElementById("fc-yt-logo-block")) return;
    var shield = document.createElement("div");
    shield.id = "fc-yt-logo-block";
    shield.setAttribute("aria-hidden", "true");
    box.appendChild(shield);
  }

  function addMoreUi(box, opts) {
    if (!box || !opts.videosUrl || document.getElementById("fc-more-btn")) return;
    var button = document.createElement("button");
    button.id = "fc-more-btn";
    button.type = "button";
    button.setAttribute("aria-label", opts.moreLabel || "More videos");
    var panel = document.createElement("div");
    panel.id = "fc-more-panel";
    panel.hidden = true;
    box.appendChild(button);
    box.appendChild(panel);

    function closePanel() {
      panel.hidden = true;
      panel.innerHTML = "";
    }

    function render(videos) {
      panel.innerHTML = "";
      var close = document.createElement("button");
      close.type = "button";
      close.className = "fc-more-close";
      close.textContent = "×";
      close.addEventListener("click", closePanel);
      panel.appendChild(close);
      var heading = document.createElement("p");
      heading.className = "fc-more-title";
      heading.textContent = opts.moreLabel || "More videos";
      panel.appendChild(heading);
      videos.forEach(function (video) {
        var id = video && video.video_id;
        if (!id) return;
        var item = document.createElement("button");
        item.type = "button";
        item.className = "fc-more-item";
        if (video.thumbnail_url) {
          var img = document.createElement("img");
          img.src = video.thumbnail_url;
          img.alt = "";
          item.appendChild(img);
        }
        var title = document.createElement("span");
        title.textContent = video.title || id;
        item.appendChild(title);
        item.addEventListener("click", function () {
          closePanel();
          if (opts.onRelated) opts.onRelated(id);
        });
        panel.appendChild(item);
      });
      panel.hidden = false;
    }

    button.addEventListener("click", function (event) {
      event.preventDefault();
      event.stopPropagation();
      if (!panel.hidden) {
        closePanel();
        return;
      }
      fetch(opts.videosUrl, {
        credentials: "same-origin",
        headers: { Accept: "application/json" },
      })
        .then(function (res) {
          return res.json();
        })
        .then(function (data) {
          if (!data || !data.ok || !data.videos) return;
          render(data.videos);
        })
        .catch(function () {});
    });
  }

  function install(opts) {
    opts = opts || {};
    var box =
      opts.box ||
      document.getElementById("player-box") ||
      document.getElementById("player-wrap");
    var getVideoId =
      opts.getVideoId ||
      function () {
        return global.videoId;
      };
    addLogoShield(box);
    addMoreUi(box, opts);
    trapExit(opts.onRelated, getVideoId);
    setInterval(function () {
      var player = opts.getPlayer ? opts.getPlayer() : global.player;
      if (!player || !player.getVideoData) return;
      try {
        var next = player.getVideoData().video_id;
        if (next && next !== getVideoId() && opts.onRelated) opts.onRelated(next);
      } catch (err) {}
    }, 800);
  }

  watchIframes();
  global.FamilyPlayerGuard = {
    install: install,
    youtubeLeaveId: youtubeLeaveId,
    isYouTubeUrl: isYouTubeUrl,
  };
})(window);
