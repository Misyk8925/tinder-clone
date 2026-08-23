<#import "connect-components.ftl" as connect>
<!DOCTYPE html>
<html lang="${(locale.currentLanguageTag)!'en'}" data-connect-theme="true" data-auth-screen="expired">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light dark">
  <title>Connect — Session expired</title>
  <link rel="stylesheet" href="${url.resourcesPath}/css/login.css?v=${connect.cssFingerprint}">
</head>
<body>
  <main class="auth-page">
    <section class="auth-shell auth-shell-state" aria-label="Connect session expired">
      <@connect.brandPanel
        eyebrow="One quick reset"
        title="Good connections are worth another click."
        copy="Your account is safe. The sign-in page simply stayed open a little too long."
      />

      <div class="auth-panel">
        <div class="form-container state-container">
          <div class="state-symbol" aria-hidden="true">↻</div>
          <div class="form-heading state-heading">
            <span class="form-kicker">Session expired</span>
            <h1>Let’s get you back in.</h1>
            <p>No details were lost. Continue the current flow or start again with a fresh sign-in.</p>
          </div>

          <div class="state-actions">
            <a id="loginContinueLink" class="btn-primary btn-link" href="${url.loginAction}">Continue sign in</a>
            <a id="loginRestartLink" class="btn-secondary btn-link" href="${url.loginRestartFlowUrl}">Start again</a>
          </div>
        </div>
      </div>
    </section>
  </main>
</body>
</html>
