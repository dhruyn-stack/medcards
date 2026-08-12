package com.medcards

/**
 * A handful of cards on first launch so the integrated-review idea is visible
 * immediately. Delete them from Browse whenever you like.
 */
object SampleData {

    private fun note(
        front: String,
        back: String,
        subject: String,
        topic: String,
        tags: List<String>
    ): List<Card> {
        val indices = Cloze.indices(front)
        return if (indices.isEmpty()) {
            listOf(Card(front = front, back = back, subject = subject, topic = topic, tags = tags))
        } else {
            indices.map {
                Card(front = front, back = back, subject = subject, topic = topic,
                    tags = tags, clozeIndex = it)
            }
        }
    }

    fun starterCards(): List<Card> = buildList {

        // --- Topic: Diabetes Mellitus (spans 5 subjects) ---
        addAll(note(
            "Which cells of the islets of Langerhans secrete insulin?",
            "Beta cells — about 65–80% of islet cells, centrally located.",
            "Anatomy", "Diabetes Mellitus", listOf("Endocrine")
        ))
        addAll(note(
            "Insulin lowers blood glucose mainly by recruiting the {{c1::GLUT4}} transporter in {{c2::skeletal muscle and adipose tissue}}.",
            "GLUT4 is the only insulin-responsive glucose transporter.",
            "Physiology", "Diabetes Mellitus", listOf("Endocrine", "High yield")
        ))
        addAll(note(
            "What is the underlying lesion in Type 1 DM?",
            "Autoimmune T-cell mediated destruction of pancreatic beta cells, with insulitis and islet autoantibodies (anti-GAD65, IA-2, insulin).",
            "Pathology", "Diabetes Mellitus", listOf("Endocrine")
        ))
        addAll(note(
            "Metformin's main mechanism is {{c1::activation of AMPK}}, which reduces {{c2::hepatic gluconeogenesis}}.",
            "First-line in T2DM. Main risk: lactic acidosis in renal impairment.",
            "Pharmacology", "Diabetes Mellitus", listOf("Endocrine", "High yield")
        ))
        addAll(note(
            "Diagnostic HbA1c threshold for diabetes mellitus?",
            "HbA1c ≥ 6.5%. (Fasting glucose ≥ 126 mg/dL, 2-h OGTT ≥ 200 mg/dL, or random ≥ 200 mg/dL with symptoms.)",
            "Medicine", "Diabetes Mellitus", listOf("Endocrine", "High yield")
        ))

        // --- Topic: Tuberculosis (spans 3 subjects) ---
        addAll(note(
            "Mycobacterium tuberculosis is stained by the {{c1::Ziehl–Neelsen}} method because of its {{c2::mycolic acid}} rich cell wall.",
            "Acid-fast bacilli appear red against a blue background.",
            "Microbiology", "Tuberculosis", listOf("Infectious disease")
        ))
        addAll(note(
            "What is the characteristic histological lesion of tuberculosis?",
            "Caseating granuloma: central caseous necrosis surrounded by epithelioid histiocytes, Langhans giant cells and a lymphocyte cuff.",
            "Pathology", "Tuberculosis", listOf("Infectious disease", "High yield")
        ))
        addAll(note(
            "Which anti-TB drug causes optic neuritis?",
            "Ethambutol — dose-dependent retrobulbar optic neuritis with loss of red-green discrimination.",
            "Pharmacology", "Tuberculosis", listOf("Infectious disease")
        ))

        // --- Topic: Heart Failure (spans 3 subjects) ---
        addAll(note(
            "Define ejection fraction and give the normal range.",
            "EF = stroke volume / end-diastolic volume. Normal 55–70%.",
            "Physiology", "Heart Failure", listOf("Cardiovascular")
        ))
        addAll(note(
            "What are the classic lung findings in left heart failure?",
            "Heavy, congested lungs with 'heart failure cells' — haemosiderin-laden alveolar macrophages.",
            "Pathology", "Heart Failure", listOf("Cardiovascular")
        ))
        addAll(note(
            "Which four drug classes reduce mortality in HFrEF?",
            "ARNI (or ACEi/ARB), beta-blocker, MRA, and SGLT2 inhibitor.",
            "Medicine", "Heart Failure", listOf("Cardiovascular", "High yield")
        ))
    }
}
