const { contextBridge } = require('electron');

contextBridge.exposeInMainWorld('neuroKaraoke', {
  version: '1.0.0'
});
