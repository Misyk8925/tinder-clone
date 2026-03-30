<!DOCTYPE html>
<html lang="${locale!'en'}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>tinder – Create Account</title>
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

    <h1 class="card-title">Create your account</h1>

    <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
      <div class="alert alert-${message.type}">
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
          <button type="button" class="toggle-pw" onclick="togglePassword('password')" aria-label="Toggle password">
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
          <button type="button" class="toggle-pw" onclick="togglePassword('password-confirm')" aria-label="Toggle password">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
          </button>
        </div>
        <#if messagesPerField.existsError('password-confirm')>
          <span class="field-error">${kcSanitize(messagesPerField.getFirstError('password-confirm'))?no_esc}</span>
        </#if>
      </div>

      <button type="submit" class="btn-primary">Create Account</button>

    </form>

    <div class="register-row">
      Already have an account? <a href="${url.loginUrl}">Sign in</a>
    </div>

  </main>

</div>

<script>
  function togglePassword(id) {
    var pw = document.getElementById(id);
    pw.type = pw.type === 'password' ? 'text' : 'password';
  }
</script>

</body>
</html>
