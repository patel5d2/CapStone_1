/*
 * Shared Clerk wiring for every page in the directory.
 *
 * Clerk is the identity provider: it owns sign-up, sign-in, email verification and
 * password management. This file loads Clerk, renders the auth controls in the nav,
 * and hands API calls a short-lived session token that the Spring Boot backend
 * validates against Clerk's JWKS endpoint.
 *
 * Usage on a page:
 *   <script src="/js/clerk-auth.js"></script>
 *   ClerkAuth.ready.then(...)          // Clerk finished loading
 *   ClerkAuth.fetch('/student/profile') // same as fetch(), but authenticated
 *   ClerkAuth.requireSignedIn()         // bounce anonymous visitors to /login.html
 */
(function () {
  'use strict';

  // Publishable keys are safe to ship to the browser — that is what they are for.
  // The matching secret key stays server-side in .env and is never referenced here.
  var PUBLISHABLE_KEY = 'pk_test_bWlnaHR5LWVzY2FyZ290LTY1NjIuY2xlcmsuYWNjb3VudHMuZGV2JA';
  var CLERK_JS = 'https://cdn.jsdelivr.net/npm/@clerk/clerk-js@6.32.1/dist/clerk.browser.js';

  var ready = new Promise(function (resolve, reject) {
    var script = document.createElement('script');
    script.src = CLERK_JS;
    script.crossOrigin = 'anonymous';
    script.setAttribute('data-clerk-publishable-key', PUBLISHABLE_KEY);
    script.onerror = function () {
      reject(new Error('Could not load Clerk from the CDN'));
    };
    script.onload = function () {
      window.Clerk.load()
        .then(function () { resolve(window.Clerk); })
        .catch(reject);
    };
    document.head.appendChild(script);
  });

  /**
   * fetch() with the caller's Clerk session token attached.
   *
   * Session tokens are short-lived by design (60s on this instance), so the token is
   * requested per call rather than cached — Clerk refreshes it transparently.
   * A 401 means the session lapsed, so the visitor is sent back to sign in.
   */
  function authFetch(url, options) {
    options = options || {};

    return ready.then(function (clerk) {
      if (!clerk.session) {
        window.location.href = '/login.html';
        return new Promise(function () { /* navigating away; never settles */ });
      }
      return clerk.session.getToken();
    }).then(function (token) {
      var headers = new Headers(options.headers || {});
      headers.set('Authorization', 'Bearer ' + token);
      if (options.body && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
      }
      return fetch(url, Object.assign({}, options, { headers: headers }));
    });
  }

  /**
   * Sends anonymous visitors to the sign-in page. Page-level gating has to happen
   * here rather than in Spring Security: a browser navigating to a URL cannot attach
   * an Authorization header, so the server protects the data, not the HTML shell.
   */
  function requireSignedIn() {
    return ready.then(function (clerk) {
      if (!clerk.user) {
        window.location.href = '/login.html';
        return null;
      }
      return clerk.user;
    });
  }

  /**
   * Renders the nav's auth controls into every [data-clerk-auth] element: a user
   * button when signed in, sign-in and sign-up buttons when signed out.
   */
  function mountNavControls() {
    return ready.then(function (clerk) {
      document.querySelectorAll('[data-clerk-auth]').forEach(function (slot) {
        slot.innerHTML = '';

        if (clerk.user) {
          var userButton = document.createElement('div');
          slot.appendChild(userButton);
          clerk.mountUserButton(userButton, { afterSignOutUrl: '/index.html' });
          return;
        }

        var signIn = document.createElement('a');
        signIn.href = '#';
        signIn.className = 'nav-link';
        signIn.textContent = 'Sign In';
        signIn.addEventListener('click', function (event) {
          event.preventDefault();
          clerk.openSignIn({ forceRedirectUrl: '/search.html' });
        });

        var signUp = document.createElement('a');
        signUp.href = '#';
        signUp.className = 'btn-outline-sm';
        signUp.textContent = 'Sign Up';
        signUp.addEventListener('click', function (event) {
          event.preventDefault();
          clerk.openSignUp({ forceRedirectUrl: '/profile.html' });
        });

        slot.appendChild(signIn);
        slot.appendChild(signUp);
      });
      return clerk;
    });
  }

  window.ClerkAuth = {
    ready: ready,
    fetch: authFetch,
    requireSignedIn: requireSignedIn,
    mountNavControls: mountNavControls
  };

  document.addEventListener('DOMContentLoaded', function () {
    mountNavControls().catch(function (err) {
      console.error('Clerk failed to load:', err);
    });
  });
})();
