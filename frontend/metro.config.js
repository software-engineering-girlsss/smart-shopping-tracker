// metro.config.js
// Metro chokes on `import.meta` which some ESM builds (e.g. zustand/esm) use.
// Force CJS resolution by preferring 'main' over 'module' for web.
const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// Prefer CJS entry points over ESM ones to avoid import.meta in bundles.
config.resolver.resolverMainFields = ['react-native', 'browser', 'main', 'module'];

// Explicit alias: zustand's package.json 'exports' map steers to .mjs — override.
config.resolver.extraNodeModules = {
  ...config.resolver.extraNodeModules,
  zustand: require('path').resolve(__dirname, 'node_modules/zustand'),
};

// Ensure Metro resolves the CJS files of zustand directly.
const origResolveRequest = config.resolver.resolveRequest;
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === 'zustand' || moduleName === 'zustand/middleware') {
    const path = require('path');
    const base = path.resolve(__dirname, 'node_modules/zustand');
    const file = moduleName === 'zustand' ? 'index.js' : 'middleware.js';
    return { filePath: path.join(base, file), type: 'sourceFile' };
  }
  if (origResolveRequest) return origResolveRequest(context, moduleName, platform);
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
