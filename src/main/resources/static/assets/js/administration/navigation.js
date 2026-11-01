/** View-only navigation. Hiding a panel does not reset or submit its forms. */
export function initializeAdminNavigation(root) {
  const groups = new Map();
  for (const list of root.querySelectorAll('[data-admin-tab-group]')) {
    const name = list.dataset.adminTabGroup;
    groups.set(name, {
      list,
      tabs: [...list.querySelectorAll('[role="tab"]')],
      panels: [...root.querySelectorAll('[data-admin-panel-group]')]
        .filter(panel => panel.dataset.adminPanelGroup === name)
    });
  }

  function activate(name, key, focus = false) {
    const group = groups.get(name);
    if (!group?.tabs.some(tab => tab.dataset.adminTab === key)) return false;
    for (const tab of group.tabs) {
      const selected = tab.dataset.adminTab === key;
      tab.setAttribute('aria-selected', String(selected));
      tab.tabIndex = selected ? 0 : -1;
      if (selected && focus) tab.focus();
    }
    for (const panel of group.panels) panel.hidden = panel.dataset.adminPanel !== key;
    return true;
  }
  const selected = name => groups.get(name).tabs.find(tab => tab.getAttribute('aria-selected') === 'true').dataset.adminTab;
  function updateHash() {
    const primary = selected('sections');
    const hash = '#' + primary + (primary === 'collection' ? '/' + selected('collection') : primary === 'topics' ? '/' + selected('topics') : '');
    if (location.hash !== hash) history.pushState(null, '', hash);
  }
  function readHash() {
    const [primary, secondary, extra] = location.hash.slice(1).split('/');
    if (extra !== undefined || !activate('sections', primary)) {
      activate('sections', 'collection');
      activate('collection', 'list');
      return;
    }
    if (primary === 'collection') activate('collection', secondary || 'list') || activate('collection', 'list');
    if (primary === 'topics') activate('topics', secondary || 'classification') || activate('topics', 'classification');
  }
  for (const [name, group] of groups) {
    for (const tab of group.tabs) {
      const choose = () => { activate(name, tab.dataset.adminTab); updateHash(); };
      tab.addEventListener('click', choose);
      tab.addEventListener('keydown', event => {
        const index = group.tabs.indexOf(tab);
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? group.tabs.length - 1
          : event.key === 'ArrowRight' ? (index + 1) % group.tabs.length
          : event.key === 'ArrowLeft' ? (index + group.tabs.length - 1) % group.tabs.length : null;
        if (next === null) return;
        event.preventDefault();
        activate(name, group.tabs[next].dataset.adminTab, true);
        updateHash();
      });
    }
  }
  window.addEventListener('popstate', readHash);
  window.addEventListener('hashchange', readHash);
  readHash();
  return {
    isCollection() { return selected('sections') === 'collection'; },
    openCollection(key) {
      if (!groups.get('collection').tabs.some(tab => tab.dataset.adminTab === key)) return;
      activate('sections', 'collection');
      activate('collection', key);
      updateHash();
    }
  };
}
