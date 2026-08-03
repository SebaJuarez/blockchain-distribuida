// Bundle vendored de Preact + htm (sin build step, sin CDN).
// Preact: https://github.com/preactjs/preact  (10.26.4)
// htm: https://github.com/developit/htm (3.1.1)
import htm from './htm.module.js';
import { h, render, Component, Fragment } from './preact.module.js';
export { h, render, Component, Fragment };
export const html = htm.bind(h);
