import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import { UpdatePrompt } from './components/UpdatePrompt';
import './lib/pwa'; // registers the install-prompt listener before React mounts
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
    <UpdatePrompt />
  </StrictMode>,
);
