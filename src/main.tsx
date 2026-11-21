import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import { UpdatePrompt } from './components/UpdatePrompt';
import { RotateOverlay } from './components/RotateOverlay';
import './lib/pwa'; // registers the install-prompt listener before React mounts
import './lib/orientation'; // phones portrait-only, larger screens rotate freely
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
    <UpdatePrompt />
    <RotateOverlay />
  </StrictMode>,
);
