<#import "connect-components.ftl" as connect>
<!DOCTYPE html>
<html lang="${(locale.currentLanguageTag)!'en'}" data-connect-theme="true" data-auth-screen="register">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light dark">
  <title>Connect — Create account</title>
  <link rel="stylesheet" href="${url.resourcesPath}/css/login.css">
  <script src="${url.resourcesPath}/js/login.js" defer></script>
</head>
<body>
  <main class="auth-page">
    <section class="auth-shell auth-shell-register" aria-label="Create a Connect account">
      <@connect.brandPanel
        eyebrow="Start something real"
        title="Your next good conversation starts here."
        copy="Create your account, set your preferences and meet people on the same wavelength."
      />

      <div class="auth-panel">
        <div class="form-container form-container-register">
          <div class="form-heading">
            <span class="form-kicker">Join Connect</span>
            <h1>Create your account</h1>
            <p>A few details, then you are ready to start connecting.</p>
          </div>

          <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="alert alert-${message.type}" role="alert">
              ${kcSanitize(message.summary)?no_esc}
            </div>
          </#if>

          <form id="kc-register-form" action="${url.registrationAction}" method="post">
            <#if !realm.registrationEmailAsUsername>
              <div class="name-row">
                <div class="form-group">
                  <label for="firstName">First name</label>
                  <input
                    type="text"
                    id="firstName"
                    name="firstName"
                    value="${(register.formData.firstName!'')}"
                    autocomplete="given-name"
                    class="<#if messagesPerField.existsError('firstName')>input-error</#if>"
                  />
                  <#if messagesPerField.existsError('firstName')>
                    <span class="field-error">${kcSanitize(messagesPerField.getFirstError('firstName'))?no_esc}</span>
                  </#if>
                </div>

                <div class="form-group">
                  <label for="lastName">Last name</label>
                  <input
                    type="text"
                    id="lastName"
                    name="lastName"
                    value="${(register.formData.lastName!'')}"
                    autocomplete="family-name"
                    class="<#if messagesPerField.existsError('lastName')>input-error</#if>"
                  />
                  <#if messagesPerField.existsError('lastName')>
                    <span class="field-error">${kcSanitize(messagesPerField.getFirstError('lastName'))?no_esc}</span>
                  </#if>
                </div>
              </div>
            </#if>

            <div class="form-group">
              <label for="email">Email</label>
              <input
                type="email"
                id="email"
                name="email"
                value="${(register.formData.email!'')}"
                autocomplete="email"
                class="<#if messagesPerField.existsError('email')>input-error</#if>"
              />
              <#if messagesPerField.existsError('email')>
                <span class="field-error">${kcSanitize(messagesPerField.getFirstError('email'))?no_esc}</span>
              </#if>
            </div>

            <#if !realm.registrationEmailAsUsername>
              <div class="form-group">
                <label for="username">Username</label>
                <input
                  type="text"
                  id="username"
                  name="username"
                  value="${(register.formData.username!'')}"
                  autocomplete="username"
                  class="<#if messagesPerField.existsError('username')>input-error</#if>"
                />
                <#if messagesPerField.existsError('username')>
                  <span class="field-error">${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}</span>
                </#if>
              </div>
            </#if>

            <div class="password-grid">
              <div class="form-group">
                <label for="password">Password</label>
                <div class="password-wrap">
                  <input
                    type="password"
                    id="password"
                    name="password"
                    autocomplete="new-password"
                    class="<#if messagesPerField.existsError('password','password-confirm')>input-error</#if>"
                  />
                  <button type="button" class="toggle-pw" data-password-toggle aria-controls="password" aria-label="Show password">
                    <@connect.eyeIcon />
                  </button>
                </div>
                <#if messagesPerField.existsError('password')>
                  <span class="field-error">${kcSanitize(messagesPerField.getFirstError('password'))?no_esc}</span>
                </#if>
              </div>

              <div class="form-group">
                <label for="password-confirm">Confirm password</label>
                <div class="password-wrap">
                  <input
                    type="password"
                    id="password-confirm"
                    name="password-confirm"
                    autocomplete="new-password"
                    class="<#if messagesPerField.existsError('password-confirm')>input-error</#if>"
                  />
                  <button type="button" class="toggle-pw" data-password-toggle aria-controls="password-confirm" aria-label="Show password">
                    <@connect.eyeIcon />
                  </button>
                </div>
                <#if messagesPerField.existsError('password-confirm')>
                  <span class="field-error">${kcSanitize(messagesPerField.getFirstError('password-confirm'))?no_esc}</span>
                </#if>
              </div>
            </div>

            <button type="submit" class="btn-primary">Create account</button>
          </form>

          <div class="switch-row">
            Already have an account? <a class="login-link" href="${url.loginUrl}">Sign in</a>
          </div>
        </div>
      </div>
    </section>
  </main>
</body>
</html>
