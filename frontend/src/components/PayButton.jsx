import { useRef, useState } from 'react';
import { pay } from '../api';
import { useToast } from '../toast';

export default function PayButton({ auctionId, bidder, onPaid }) {
  const notify = useToast();
  const [pending, setPending] = useState(false);
  const [receipt, setReceipt] = useState(null);
  const [error, setError] = useState('');
  const idemKey = useRef(crypto.randomUUID());

  const submit = async () => {
    setPending(true);
    setError('');
    try {
      const d = await pay(auctionId, bidder, idemKey.current);
      setReceipt(d.receipt);
      onPaid?.();
    } catch (e) {
      setError(e.message);
      notify(e.message, 'error');
    } finally {
      setPending(false);
    }
  };

  if (receipt) {
    return <p className="badge badge-live">PAID — receipt {receipt.id}</p>;
  }
  return (
    <div className="pay-box">
      <p>You won this auction! Settle your winning bid securely.</p>
      <button onClick={submit} disabled={pending}>{pending ? 'Processing…' : 'Pay now'}</button>
      {error && <p className="error">{error}</p>}
    </div>
  );
}
