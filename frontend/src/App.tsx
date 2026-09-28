import { Route, Routes } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { AwardEventPage } from './pages/AwardEventPage';
import { AwardEventsPage } from './pages/AwardEventsPage';
import { CallbackPage } from './pages/CallbackPage';
import { ProposalPage } from './pages/ProposalPage';

function App() {
  return (
    <Routes>
      <Route path="/callback" element={<CallbackPage />} />
      <Route path="/" element={<AppLayout><AwardEventsPage /></AppLayout>} />
      <Route path="/award-events/:id" element={<AppLayout><AwardEventPage /></AppLayout>} />
      <Route path="/proposals/:id" element={<AppLayout><ProposalPage /></AppLayout>} />
    </Routes>
  );
}

export default App;
