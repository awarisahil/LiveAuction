import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useToast } from '../toast';

const CATEGORIES = ['ELECTRONICS', 'FASHION', 'COLLECTIBLES', 'HOME', 'ART', 'SPORTS', 'VEHICLES', 'OTHER'];
const DURATIONS = [
  { label: '1 minute', value: 1 },
  { label: '5 minutes', value: 5 },
  { label: '10 minutes', value: 10 },
  { label: '30 minutes', value: 30 },
  { label: '1 hour', value: 60 },
  { label: '4 hours', value: 240 },
  { label: '24 hours', value: 1440 },
  { label: '7 days', value: 10080 },
];
const EMOJIS = ['\uD83D\uDCF7', '\uD83C\uDFAE', '\uD83D\uDCBB', '\uD83D\uDC55', '\uD83D\uDC60', '\uD83D\uDC8D', '\uD83C\uDFC9', '\uD83C\uDFB8', '\uD83D\uDDBC\uFE0F', '\uD83D\uDEB4', '\uD83D\uDDA8\uFE0F', '\uD83D\uDCE6'];

export default function SellPage() {
  const navigate = useNavigate();
  const notify = useToast();
  const [form, setForm] = useState({
    title: '',
    category: 'ELECTRONICS',
    description: '',
    startPrice: '10.00',
    minIncrement: '1.00',
    durationMinutes: 10,
    imageEmoji: EMOJIS[0],
    imageUrl: '',
  });
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setPending(true);
    try {
      const auction = await api.createAuction({
        title: form.title,
        description: form.description,
        category: form.category,
        startPriceCents: Math.round(parseFloat(form.startPrice) * 100),
        minIncrementCents: Math.round(parseFloat(form.minIncrement) * 100),
        durationMinutes: Number(form.durationMinutes),
        imageEmoji: form.imageEmoji,
        imageUrl: form.imageUrl || null,
      });
      notify('Your listing is live!', 'success');
      navigate(`/auction/${auction.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setPending(false);
    }
  };

  return (
    <main className="auth-page">
      <form className="auth-card wide" onSubmit={submit}>
        <h2>List an item</h2>
        <label>Title</label>
        <input value={form.title} onChange={set('title')} required maxLength={90} placeholder="e.g. Vintage film camera" />

        <label>Category</label>
        <select value={form.category} onChange={set('category')}>
          {CATEGORIES.map((c) => <option key={c} value={c}>{c.toLowerCase()}</option>)}
        </select>

        <label>Description</label>
        <textarea value={form.description} onChange={set('description')} rows={3} maxLength={600} placeholder="Condition, provenance, shipping details..." />

        <div className="form-row">
          <div>
            <label>Starting price ($)</label>
            <input type="number" step="0.01" min="0.01" value={form.startPrice} onChange={set('startPrice')} required />
          </div>
          <div>
            <label>Min. increment ($)</label>
            <input type="number" step="0.01" min="0.01" value={form.minIncrement} onChange={set('minIncrement')} required />
          </div>
          <div>
            <label>Duration</label>
            <select value={form.durationMinutes} onChange={set('durationMinutes')}>
              {DURATIONS.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
            </select>
          </div>
        </div>

        <label>Photo (emoji)</label>
        <div className="emoji-picker">
          {EMOJIS.map((emoji) => (
            <button
              type="button"
              key={emoji}
              className={`emoji-option ${form.imageEmoji === emoji ? 'emoji-active' : ''}`}
              onClick={() => setForm((f) => ({ ...f, imageEmoji: emoji }))}
            >
              {emoji}
            </button>
          ))}
        </div>

        <label>Photo URL (optional)</label>
        <input type="url" value={form.imageUrl} onChange={set('imageUrl')} placeholder="https://..." />

        <p className="muted small">When your item sells, the winning bidder pays from their wallet and your proceeds (95%) are credited instantly.</p>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={pending}>{pending ? 'Listing…' : 'Start auction'}</button>
      </form>
    </main>
  );
}
