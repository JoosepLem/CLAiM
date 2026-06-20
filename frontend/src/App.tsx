import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import Reconciling from './pages/Reconciling';
import ReconciliationReport from './pages/ReconciliationReport';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/reconciling" element={<Reconciling />} />
        <Route path="/invoices/:invoiceId" element={<ReconciliationReport />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
