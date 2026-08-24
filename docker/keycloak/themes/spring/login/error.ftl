<#import "connect-components.ftl" as connect>
<!DOCTYPE html>
<html lang="${(locale.currentLanguageTag)!'en'}" data-connect-theme="true" data-auth-screen="error">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light dark">
  <meta name="theme-color" content="#9cce2b">
  <title>Lunari — Sign-in issue</title>
  <link rel="icon" type="image/svg+xml" href="${url.resourcesPath}/img/connect-icon.svg">
  <link rel="stylesheet" href="${url.resourcesPath}/css/login.css?v=${connect.cssFingerprint}">
</head>
<body>
  <main class="auth-page">
    <section class="auth-shell auth-shell-state" aria-label="Lunari sign-in issue">
      <@connect.brandPanel
        eyebrow="A small detour"
        title="Something interrupted the sign-in flow."
        copy="Your account remains safe. Return to Lunari and try the sign-in flow once more."
      />

      <div class="auth-panel">
        <div class="form-container state-container">
          <div class="state-symbol state-symbol-error" aria-hidden="true">!</div>
          <div class="form-heading state-heading">
            <span class="form-kicker">Unable to continue</span>
            <h1>Let’s try that again.</h1>
            <p class="state-message" role="alert">${kcSanitize(message.summary)?no_esc}</p>
          </div>

          <div class="state-actions">
            <#if client?? && client.baseUrl?has_content>
              <a id="backToApplication" class="btn-primary btn-link" href="${client.baseUrl}">Back to Lunari</a>
            <#else>
              <a class="btn-primary btn-link" href="${url.loginUrl}">Return to sign in</a>
            </#if>
          </div>
        </div>
      </div>
    </section>
  </main>
</body>
</html>
