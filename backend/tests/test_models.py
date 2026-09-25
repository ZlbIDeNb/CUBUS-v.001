from decimal import Decimal
import unittest

from app.models import CloseApplicationRequest, PaymentType


class CloseApplicationRequestTests(unittest.TestCase):
    def test_mixed_payment_maps_both_amounts(self):
        command = CloseApplicationRequest(
            payment_type=PaymentType.MIXED,
            cash_sum=Decimal("1200"),
            card_sum=Decimal("800"),
        )

        self.assertEqual(command.crm_attributes(), {
            "status": "Выполнено",
            "payment_type": "Эквайринг + Наличные",
            "cash_sum": 1200,
            "card_sum": 800,
        })

    def test_cash_payment_does_not_send_card_amount(self):
        command = CloseApplicationRequest(
            payment_type=PaymentType.CASH,
            cash_sum=Decimal("2000"),
        )

        self.assertEqual(command.crm_attributes(), {
            "status": "Выполнено",
            "payment_type": "Наличные",
            "cash_sum": 2000,
        })


if __name__ == "__main__":
    unittest.main()
