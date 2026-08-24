<#assign cssFingerprint="1d54f36cc8ea">
<#assign jsFingerprint="6fa24d7b98ec">

<#macro brandPanel eyebrow title copy>
  <aside class="brand-panel">
    <div class="brand-lockup" aria-label="Lunari">
      <span class="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 48 48" fill="none">
          <path d="M31.8 10.7a14.8 14.8 0 1 0 6 25.6 13.2 13.2 0 0 1-6-25.6Z" fill="currentColor" stroke="none"/>
          <path d="M36.5 8.8c.35 2.4 2.2 4.25 4.6 4.6-2.4.35-4.25 2.2-4.6 4.6-.35-2.4-2.2-4.25-4.6-4.6 2.4-.35 4.25-2.2 4.6-4.6Z" fill="currentColor" stroke="none"/>
        </svg>
      </span>
      <span class="brand-name">Lunari</span>
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
