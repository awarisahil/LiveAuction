import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, newIdemKey } from '../api';
import { useAuth } from '../auth';
import { useToast } from '../toast';
import { fmt } from './AuctionCard';

export default function PayButton({ auctionId, onPaid }) {
  const { refresh } = useAuth();
  const notify = useToast();
  const [pending, setPending] = useState(false);
  const [receipt, setReceipt] = useState(null);
  const [error, setError] = useState('');
  const idemKey = useRef(newIdemKey());

  const submit = async () => {
    setPending(true);
    setError('');
    try {
      const d = await api.pay(auctionId, idemKey.current);
      setReceipt(d.receipt);
      onPaid?.();
      refresh().catch(() => {});
    } catch (e) {
      setError(e.message);
      notify(e.message, 'error');
    } finally {
      setPending(false);
    }
  };

  if (receipt) {
    return (
      <div className="pay-box">
        <p className="badge badge-live">PAID — receipt {receipt.id}</p>
        <p className="muted">
          {fmt(receipt.amountCents)} settled (incl. {fmt(receipt.feeCents)} platform fee)
        </p>
      </div>
    );
  }
  return (
    <div className="pay-box">
      <p>You won this auction! Settle it from your wallet — the seller is paid out automatically (5% platform fee).</p>
      <button onClick={submit} disabled={pending}>{pending ? 'Processing…' : 'Pay from wallet'}</button>
      {error && (
        <p className="error">
          {error}{' '}
          {error.includes('Insufficient') && <Link to="/profile">Deposit funds on your profile</Link>}
        </p>
      )}
    </div>
  );
}
