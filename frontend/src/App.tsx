import { Route, Routes } from 'react-router-dom';
import { CallbackPage } from './pages/CallbackPage';
import { HomePage } from './pages/HomePage';

function App() {
  return (
    <Routes>
      <Route path="/callback" element={<CallbackPage />} />
      <Route path="/" element={<HomePage />} />
    </Routes>
  );
}

export default App;
