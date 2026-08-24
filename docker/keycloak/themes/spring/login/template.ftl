<#import "connect-components.ftl" as connect>
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="${(locale.currentLanguageTag)!'en'}" data-connect-theme="true" data-auth-screen="flow">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light dark">
  <meta name="theme-color" content="#9cce2b">
  <meta name="robots" content="noindex, nofollow">
  <title>${msg("loginTitle", (realm.displayName!'Lunari'))}</title>
  <link rel="icon" type="image/svg+xml" href="${url.resourcesPath}/img/connect-icon.svg">
  <link rel="stylesheet" href="${url.resourcesPath}/css/login.css?v=${connect.cssFingerprint}">
  <script type="importmap">
    {
      "imports": {
        "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
      }
    }
  </script>
  <#if scripts??>
    <#list scripts as script>
      <script src="${script}" defer></script>
    </#list>
  </#if>
  <script type="module">
    import { startSessionPolling } from "${url.resourcesPath}/js/authChecker.js";
    startSessionPolling("${url.ssoLoginInOtherTabsUrl?no_esc}");
  </script>
  <#if authenticationSession??>
    <script type="module">
      import { checkAuthSession } from "${url.resourcesPath}/js/authChecker.js";
      checkAuthSession("${authenticationSession.authSessionIdHash}");
    </script>
  </#if>
</head>
<body class="${bodyClass}">
  <main class="auth-page">
    <section class="auth-shell auth-shell-flow" aria-label="Lunari secure account flow">
      <@connect.brandPanel
        eyebrow="Your account, securely"
        title="Keep the conversation moving."
        copy="Sign in, recover access or confirm an account action without leaving the Lunari experience."
      />

      <div class="auth-panel">
        <div class="form-container flow-container">
          <#if realm.internationalizationEnabled && locale.supported?size gt 1>
            <details class="locale-switcher">
              <summary>${locale.current}</summary>
              <div class="locale-options">
                <#list locale.supported as language>
                  <a href="${language.url}">${language.label}</a>
                </#list>
              </div>
            </details>
          </#if>

          <div class="form-heading flow-heading">
            <span class="form-kicker">Lunari account</span>
            <h1 id="kc-page-title"><#nested "header"></h1>
            <#if displayRequiredFields>
              <p class="required-note"><span aria-hidden="true">*</span> ${msg("requiredFields")}</p>
            </#if>
          </div>

          <#if auth?has_content && auth.showUsername() && !auth.showResetCredentials()>
            <#nested "show-username">
            <div id="kc-username" class="attempted-user">
              <span id="kc-attempted-username">${auth.attemptedUsername}</span>
              <a id="reset-login" href="${url.loginRestartFlowUrl}">${msg("restartLoginTooltip")}</a>
            </div>
          </#if>

          <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="alert alert-${message.type}" role="alert">
              ${kcSanitize(message.summary)?no_esc}
            </div>
          </#if>

          <div id="kc-content" class="flow-content">
            <#nested "form">

            <#if auth?has_content && auth.showTryAnotherWayLink()>
              <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                <input type="hidden" name="tryAnotherWay" value="on">
                <button class="btn-secondary" type="submit">${msg("doTryAnotherWay")}</button>
              </form>
            </#if>

            <#nested "socialProviders">

            <#if displayInfo>
              <div id="kc-info" class="flow-info">
                <#nested "info">
              </div>
            </#if>
          </div>
        </div>
      </div>
    </section>
  </main>
</body>
</html>
</#macro>
