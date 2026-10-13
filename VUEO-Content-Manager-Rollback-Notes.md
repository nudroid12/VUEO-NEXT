# Content Manager TV rollback

Restores Content Manager / Plugins & Providers to its layout before the Settings polish patch:
repository actions, provider details and latency, Ready metrics, status feedback, row sizes and spacing.
Account polish, sidebar and subtitle provider-only patch are preserved.

Apply with the existing ZIP workflow. Paths start at repository root; no YAML files.
Based on VUEO-NEXT-main (48) and the Settings polish patch supplied on 13 October 2026.

Validation: baseline comparison of Content settings and provider rows passed; shared component
changes checked for Content Manager scope and existing function-call compatibility.
Android compilation and physical TV navigation have not been run in this environment.
