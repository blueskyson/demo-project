import { Route, Routes } from 'react-router-dom';
import { CallbackPage } from './pages/CallbackPage';
import { HomePage } from './pages/HomePage';
import { DocumentsPage } from './pages/DocumentsPage';

function App() {
  return (
    <Routes>
      <Route path="/callback" element={<CallbackPage />} />
      <Route path="/" element={<HomePage />} />
      <Route path="/documents" element={<DocumentsPage />} />
    </Routes>
  );
}

export default App;
