import { useEffect, useState } from 'react';

export default function BidForm({ minBidCents, onBid, pending, error }) {
  const minDollars = (minBidCents / 100).toFixed(2);
  const [amount, setAmount] = useState(minDollars);

  // Keep the entered amount valid when competing bids raise the minimum.
  useEffect(() => {
    setAmount((prev) => {
      const cents = Math.round(parseFloat(prev) * 100);
      return Number.isFinite(cents) && cents >= minBidCents ? prev : minDollars;
    });
  }, [minBidCents]);

  const submit = (e) => {
    e.preventDefault();
    const cents = Math.round(parseFloat(amount) * 100);
    if (!Number.isFinite(cents) || cents <= 0) return;
    onBid(cents);
  };

  return (
    <form className="bid-form" onSubmit={submit}>
      <div className="bid-row">
        <label htmlFor="bid-amount">$</label>
        <input
          id="bid-amount"
          type="number"
          step="0.01"
          min={minDollars}
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          disabled={pending}
        />
        <button type="submit" disabled={pending}>{pending ? 'Placing…' : 'Place bid'}</button>
        <button type="button" className="ghost" disabled={pending} onClick={() => setAmount(minDollars)}>
          Min ({minDollars})
        </button>
      </div>
      {error && <p className="error">{error}</p>}
    </form>
  );
}
