<#assign cssFingerprint="0de8caf975e3">
<#assign jsFingerprint="6fa24d7b98ec">

<#macro brandPanel eyebrow title copy>
  <aside class="brand-panel">
    <div class="brand-lockup" aria-label="Connect">
      <span class="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 48 48" fill="none">
          <path d="M19.2 14.7l-3.1-3.1a7.2 7.2 0 0 0-10.2 10.2l5.7 5.7a7.2 7.2 0 0 0 10.2 0l3.1-3.1"/>
          <path d="M28.8 33.3l3.1 3.1a7.2 7.2 0 0 0 10.2-10.2l-5.7-5.7a7.2 7.2 0 0 0-10.2 0l-3.1 3.1"/>
          <path d="M17.5 30.5l13-13"/>
        </svg>
      </span>
      <span class="brand-name">connect</span>
    </div>

    <div class="brand-copy">
      <span class="brand-eyebrow">${eyebrow}</span>
      <h2>${title}</h2>
      <p>${copy}</p>
    </div>

    <div class="trust-note">
      <span class="trust-dot" aria-hidden="true"></span>
      Secure sign-in powered by Keycloak
    </div>
  </aside>
</#macro>

<#macro eyeIcon>
  <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
    <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z"/>
    <circle cx="12" cy="12" r="2.6"/>
  </svg>
</#macro>
