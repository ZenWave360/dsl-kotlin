// ELK layout runs on the page's main thread in the browser smoke test; allow it time.
config.set({
    client: {
        ...(config.client || {}),
        mocha: {
            ...((config.client && config.client.mocha) || {}),
            timeout: 30000,
        },
    },
});
