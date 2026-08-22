<#import "connect-components.ftl" as connect>
<!DOCTYPE html>
<html lang="${(locale.currentLanguageTag)!'en'}" data-connect-theme="true" data-auth-screen="login">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light dark">
  <title>Connect — Sign in</title>
  <link rel="stylesheet" href="${url.resourcesPath}/css/login.css">
  <script src="${url.resourcesPath}/js/login.js" defer></script>
</head>
<body>
  <main class="auth-page">
    <section class="auth-shell" aria-label="Connect sign in">
      <@connect.brandPanel
        eyebrow="Dating with intention"
        title="Meet people worth meeting."
        copy="A calmer place for real conversations, shared interests and genuine connection."
      />

      <div class="auth-panel">
        <div class="form-container">
          <div class="form-heading">
            <span class="form-kicker">Welcome back</span>
            <h1>Sign in to Connect</h1>
            <p>Continue to your conversations and new matches.</p>
          </div>

          <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="alert alert-${message.type}" role="alert">
              ${kcSanitize(message.summary)?no_esc}
            </div>
          </#if>

          <form id="kc-form-login" action="${url.loginAction}" method="post">
            <input type="hidden" id="id-hidden-input" name="credentialId"
                   <#if auth?? && auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>

            <div class="form-group">
              <label for="username">Username or email</label>
              <input
                type="text"
                id="username"
                name="username"
                value="${(login.username!'')}"
                autofocus
                autocomplete="username"
                class="<#if messagesPerField.existsError('username','password')>input-error</#if>"
              />
              <#if messagesPerField.existsError('username')>
                <span class="field-error">${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}</span>
              </#if>
            </div>

            <div class="form-group">
              <div class="label-row">
                <label for="password">Password</label>
                <#if realm.resetPasswordAllowed>
                  <a class="text-link" href="${url.loginResetCredentialsUrl}">Forgot password?</a>
                </#if>
              </div>
              <div class="password-wrap">
                <input
                  type="password"
                  id="password"
                  name="password"
                  autocomplete="current-password"
                  class="<#if messagesPerField.existsError('username','password')>input-error</#if>"
                />
                <button type="button" class="toggle-pw" data-password-toggle aria-controls="password" aria-label="Show password">
                  <@connect.eyeIcon />
                </button>
              </div>
              <#if messagesPerField.existsError('password')>
                <span class="field-error">${kcSanitize(messagesPerField.getFirstError('password'))?no_esc}</span>
              </#if>
            </div>

            <#if realm.rememberMe && !usernameEditDisabled??>
              <label class="check-row" for="rememberMe">
                <input id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>>
                <span>Keep me signed in</span>
              </label>
            </#if>

            <button id="kc-login" name="login" type="submit" class="btn-primary">Sign in</button>
          </form>

          <#if social?? && social.providers?has_content>
            <div class="divider"><span>or continue with</span></div>
            <div class="social-list">
              <#list social.providers as p>
                <a href="${p.loginUrl}" class="btn-social btn-social-${p.providerId}">
                  <#if p.providerId == "google">
                    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 48 48">
                      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24s.92 7.54 2.56 10.78l7.97-6.19z"/>
                      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                    </svg>
                  </#if>
                  Continue with ${p.displayName}
                </a>
              </#list>
            </div>
          </#if>

          <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div class="switch-row">
              New to Connect? <a class="register-link" href="${url.registrationUrl}">Create account</a>
            </div>
          </#if>
        </div>
      </div>
    </section>
  </main>
</body>
</html>
