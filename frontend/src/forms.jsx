import { useState } from 'react';

export const tiers = ['TEENS', 'YOUTH', 'YOUNG_ADULT'];
export const defaultConfig = {
  capSize: '',
  targetSize: 4,
  genderMode: 'SEPARATED',
  activeTiers: tiers,
  lifeStageSubgroupingEnabled: true,
  ageTierBoundaries: {
    TEENS: { minAge: 13, maxAge: 15 },
    YOUTH: { minAge: 16, maxAge: 22 },
    YOUNG_ADULT: { minAge: 23, maxAge: 2147483647 },
  },
};
export function configFrom(form) {
  const fields = new FormData(form);
  return {
    capSize: Number(fields.get('capSize')),
    targetSize: Number(fields.get('targetSize')),
    genderMode: fields.get('genderMode'),
    activeTiers: fields.getAll('activeTiers'),
    lifeStageSubgroupingEnabled: fields.has('lifeStageSubgroupingEnabled'),
    ageTierBoundaries: Object.fromEntries(
      tiers.map((tier) => [
        tier,
        {
          minAge: Number(fields.get(`${tier}.minAge`)),
          maxAge:
            fields.get(`${tier}.maxAge`) === '' ? 2147483647 : Number(fields.get(`${tier}.maxAge`)),
        },
      ]),
    ),
  };
}
export function AsyncForm({ name, action, onError, children }) {
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget;
    setBusy(true);
    try {
      await action(form);
    } catch (error) {
      onError(error.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form aria-label={name} onSubmit={submit}>
      <fieldset className="form-content" disabled={busy}>
        {children}
      </fieldset>
    </form>
  );
}
export function ConfigFields({ config = defaultConfig }) {
  return (
    <div className="config-fields">
      <div className="fields">
        <label>
          Maximum participants per group
          <input
            name="capSize"
            type="number"
            min="1"
            max="2147483647"
            required
            placeholder="Required for this event"
            defaultValue={config.capSize}
          />
        </label>
        <label>
          Target participants per group
          <input
            name="targetSize"
            type="number"
            min="1"
            max="2147483647"
            required
            defaultValue={config.targetSize}
          />
        </label>
        <label>
          Gender grouping
          <select name="genderMode" defaultValue={config.genderMode}>
            <option>SEPARATED</option>
            <option>MIXED</option>
          </select>
        </label>
      </div>
      <label className="check">
        <input
          name="lifeStageSubgroupingEnabled"
          type="checkbox"
          defaultChecked={config.lifeStageSubgroupingEnabled}
        />
        Separate Young Adult students and working professionals
      </label>
      <details>
        <summary>Active tiers and age boundaries</summary>
        <p className="muted">
          Ranges are inclusive and must be contiguous. Ages below 13 are excluded with warnings.
        </p>
        {tiers.map((tier) => (
          <div className="tier-fields" key={tier}>
            <label className="check">
              <input
                name="activeTiers"
                type="checkbox"
                value={tier}
                defaultChecked={config.activeTiers.includes(tier)}
              />
              {tier}
            </label>
            <label>
              {tier} from age
              <input
                name={`${tier}.minAge`}
                type="number"
                min="13"
                max="2147483647"
                required
                defaultValue={config.ageTierBoundaries[tier].minAge}
              />
            </label>
            <label>
              {tier === 'YOUNG_ADULT' ? 'Through age (optional)' : `${tier} through age`}
              <input
                name={`${tier}.maxAge`}
                type="number"
                min="13"
                max="2147483647"
                required={tier !== 'YOUNG_ADULT'}
                defaultValue={
                  config.ageTierBoundaries[tier].maxAge === 2147483647
                    ? ''
                    : config.ageTierBoundaries[tier].maxAge
                }
              />
            </label>
          </div>
        ))}
        <p className="muted">Leave the Young Adult upper age blank for no upper limit.</p>
      </details>
    </div>
  );
}
export function GenderField({ name = 'gender', defaultValue = '' }) {
  return (
    <label>
      Gender
      <select name={name} required defaultValue={defaultValue}>
        <option value="">Choose gender</option>
        <option>MALE</option>
        <option>FEMALE</option>
      </select>
    </label>
  );
}
export function LifeStageField({ defaultValue = '' }) {
  return (
    <label>
      Life stage
      <select name="lifeStage" defaultValue={defaultValue}>
        <option value="">Choose for Young Adults</option>
        <option value="STUDENT">Student</option>
        <option value="WORKING_PROFESSIONAL">Working professional</option>
      </select>
    </label>
  );
}
