<!DOCTYPE html>
<html lang="${locale!'en'}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>tinder – Sign In</title>
  <link rel="stylesheet" href="${url.resourcesPath}/css/login.css">
</head>
<body>

<div class="page-wrap">

  <!-- Logo -->
  <div class="logo-block">
    <svg class="logo-flame" viewBox="0 0 100 90" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="flame-grad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%"   stop-color="#FD297B"/>
          <stop offset="100%" stop-color="#FF655B"/>
        </linearGradient>
      </defs>
      <path fill="url(#flame-grad)" d="
        M50 82
        C50 82 8 56 8 31
        C8 14 20 4 33 4
        C41 4 48 10 50 16
        C52 10 59 4 67 4
        C80 4 92 14 92 31
        C92 56 50 82 50 82 Z
      "/>
    </svg>
    <span class="logo-name">tinder</span>
  </div>

  <!-- Card -->
  <main class="card">

    <h1 class="card-title">Sign in to your account</h1>

    <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
      <div class="alert alert-${message.type}">
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
        <label for="password">Password</label>
        <div class="password-wrap">
          <input
            type="password"
            id="password"
            name="password"
            autocomplete="current-password"
            class="<#if messagesPerField.existsError('username','password')>input-error</#if>"
          />
          <button type="button" class="toggle-pw" onclick="togglePassword()" aria-label="Toggle password visibility">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
          </button>
        </div>
        <#if messagesPerField.existsError('password')>
          <span class="field-error">${kcSanitize(messagesPerField.getFirstError('password'))?no_esc}</span>
        </#if>
      </div>

      <button type="submit" class="btn-primary">Sign In</button>

    </form>

    <#if social?? && social.providers?has_content>
      <div class="divider"><span>or</span></div>
      <div class="social-list">
        <#list social.providers as p>
          <a href="${p.loginUrl}" class="btn-social btn-social-${p.providerId}">
            <#if p.providerId == "google">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 48 48">
                <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
              </svg>
            </#if>
            Continue with ${p.displayName}
          </a>
        </#list>
      </div>
    </#if>

    <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
      <div class="register-row">
        New to tinder? <a href="${url.registrationUrl}">Create account</a>
      </div>
    </#if>

  </main>

</div>

<script>
  function togglePassword() {
    var pw = document.getElementById('password');
    pw.type = pw.type === 'password' ? 'text' : 'password';
  }
</script>

</body>
</html>
