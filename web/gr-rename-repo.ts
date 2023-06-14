import {PluginApi} from '@gerritcodereview/typescript-api/plugin';
import {
  ActionInfo,
  ConfigInfo,
  RepoName,
} from '@gerritcodereview/typescript-api/rest-api';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';

// TODO: This should be defined and exposed by @gerritcodereview/typescript-api
type GrOverlay = Element & {
  open(): void;
  close(): void;
};

declare global {
  interface HTMLElementTagNameMap {
    'gr-rename-repo': GrRenameRepo;
  }
  interface Window {
    CANONICAL_PATH?: string;
  }
}

@customElement('gr-rename-repo')
export class GrRenameRepo extends LitElement {
  @query('#renameRepoOverlay')
  renameRepoOverlay?: GrOverlay;

  @query('#oneBox')
  oneBox?: HTMLInputElement;

  @query('#twoBox')
  twoBox?: HTMLInputElement;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: Object})
  plugin!: PluginApi;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: Object})
  config!: ConfigInfo;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: String})
  repoName!: RepoName;

  @state()
  private error?: string;

  static override styles = css`
    :host {
      display: block;
      margin-bottom: var(--spacing-xxl);
    }
    /* TODO: Find a way to use shared styles in lit elements in plugins. */
    h3 {
      font: inherit;
      margin: 0;
    }
    .error {
      color: red;
    }
  `;

  get action(): ActionInfo | undefined {
    return this.config.actions?.[this.actionId];
  }

  get actionId(): string {
    return `${this.plugin.getPluginName()}~rename`;
  }

  private renderError() {
    if (!this.error) return;
    return html`<div class="error">${this.error}</div>`;
  }

  override render() {
    if (!this.action) return;
    return html`
      <h3>${this.action.label}</h3>
      <gr-button
        title="${this.action.title}"
        ?disabled="${!this.action.enabled}"
        @click="${() => {
          this.error = undefined;
          this.renameRepoOverlay?.open();
        }}"
      >
        ${this.action.label}
      </gr-button>
      ${this.renderError()}
      <gr-overlay id="renameRepoOverlay" with-backdrop>
        <gr-dialog
          id="renameRepoOverlay"
          confirm-label="rename"
          @confirm="${this.renameRepo}"
          @cancel="${() => this.renameRepoOverlay?.close()}"
        >
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private renameRepo() {
    if (!this.action) {
      this.error = 'rename action undefined';
      this.renameRepoOverlay?.close();
      return;
    }
    if (!this.action.method) {
      this.error = 'rename action does not have a HTTP method set';
      this.renameRepoOverlay?.close();
      return;
    }
    this.error = undefined;

    const endpoint = `/projects/${encodeURIComponent(this.repoName)}/${
      this.actionId
    }`;
    return this.plugin
      .restApi()
      .send(this.action.method, endpoint)
      .then(_ => {
        this.plugin.restApi().invalidateReposCache();
        this.renameRepoOverlay?.close();
        window.location.href = `${this.getBaseUrl()}/admin/repos`;
      })
      .catch(e => {
        this.error = e;
        this.renameRepoOverlay?.close();
      });
  }

  private getBaseUrl() {
    return window.CANONICAL_PATH || '';
  }
}
